/*
 * libmemagent: JVM agent to track memory allocations
 *
 * Copyright (C) 2016 Jesper Pedersen <jesper.pedersen@comcast.net>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

#include <unistd.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include <sys/time.h>
#include <sys/types.h>

#include <jni.h>
#include <jvmti.h>
#include <jvmticmlr.h>

#include "mem-info-file.h"

FILE *file = NULL;
int depth = 10;
int statistics = 0;
long allo_count = 0;
long allo_total = 0;
int max_depth = 0;
int relative = 0;
char** includes = NULL;
int number_of_includes = 0;
double start_time = 0;
long delay = 0;
long duration = 0;

static double
get_time_ms()
{
   struct timeval t;
   gettimeofday(&t, NULL);
   return (t.tv_sec + (t.tv_usec / 1000000.0)) * 1000.0;
}

static void
clean_class_name(char *dest, size_t dest_size, char *signature) {
   int array_counter, array = 0;
   int i = 0;
   
   while (signature[i] == '[')
   {
      array++;
      i++;
   }
   
   if (signature[i] == 'Z')
   {
      strcpy(dest, "boolean");
   }
   else if (signature[i] == 'B')
   {
      strcpy(dest, "byte");
   }
   else if (signature[i] == 'C')
   {
      strcpy(dest, "char");
   }
   else if (signature[i] == 'D')
   {
      strcpy(dest, "double");
   }
   else if (signature[i] == 'F')
   {
      strcpy(dest, "float");
   }
   else if (signature[i] == 'I')
   {
      strcpy(dest, "int");
   }
   else if (signature[i] == 'J')
   {
      strcpy(dest, "long");
   }
   else if (signature[i] == 'S')
   {
      strcpy(dest, "short");
   }
   else if (signature[i] == 'L')
   {
      char *src = signature + i + 1;
      for (i = 0; i < (dest_size - 1) && src[i]; i++)
      {
         char c = src[i];

         if (c == ';')
         {
            c = 0;
         }
         dest[i] = c;
      }
   }

   for (array_counter = 0; array_counter < array; array_counter++)
   {
      strcat(dest, "[]");
   }
   
   dest[strlen(dest) + 1] = 0;
}

static void
class_name(jvmtiEnv *jvmti, jmethodID method, char *output, size_t noutput)
{
   jclass class;
   char *csig;

   (*jvmti)->GetMethodDeclaringClass(jvmti, method, &class);
   (*jvmti)->GetClassSignature(jvmti, class, &csig, NULL);

   snprintf(output, noutput, "%s", csig);

   (*jvmti)->Deallocate(jvmti, csig);
}

static void
signature_string(jvmtiEnv *jvmti, jmethodID method, char *output, size_t noutput)
{
   char *name;
   jclass class;
   char *csig;

   (*jvmti)->GetMethodName(jvmti, method, &name, NULL, NULL);
   (*jvmti)->GetMethodDeclaringClass(jvmti, method, &class);
   (*jvmti)->GetClassSignature(jvmti, class, &csig, NULL);

   char cleaned_class_name[1024];
   clean_class_name(cleaned_class_name, sizeof(cleaned_class_name), csig);
   
   snprintf(output, noutput, "%s:.%s", cleaned_class_name, name);

   (*jvmti)->Deallocate(jvmti, name);
   (*jvmti)->Deallocate(jvmti, csig);
}

static void JNICALL
callbackVMObjectAlloc(jvmtiEnv *jvmti, JNIEnv* jni, jthread thread,  jobject object, jclass object_klass, jlong size)
{
   char *allocatedClassName;
   jvmtiFrameInfo frames[depth];
   jint count;
   jint frame_count;
   int i;

   char cleaned_allocated_class_name[1024];
   char allocated_info[1024];
   char line[2048];

   int included = 0;

   if (start_time != 0)
   {
      double current_time = get_time_ms();
      if (delay != 0)
      {
         if (current_time < (start_time + delay))
            return;
      }

      if (current_time > (start_time + delay + duration))
      {
         if (file)
         {
            mem_info_close(file);
            file = NULL;
         }

         return;
      }
   }
   
   (*jvmti)->GetClassSignature(jvmti, object_klass, &allocatedClassName, NULL);
   (*jvmti)->GetStackTrace(jvmti, thread, (jint)0, (jint)depth, (jvmtiFrameInfo *)&frames, &count);

   if (includes == NULL)
   {
      included = 1;
   }
   else
   {
      char verify[2048];

      snprintf(verify, sizeof(verify), allocatedClassName);
      strcat(verify, ";");

      for (i = count - 1; i >= 0; i--)
      {
         char clz[1024];
         class_name(jvmti, frames[i].method, clz, sizeof(clz));

         strcat(verify, clz);
         strcat(verify, ";");
      }

      for (i = 0; i < number_of_includes && !included; i++)
      {
         char* entry = includes[i];

         if (strstr(verify, entry) != NULL)
            included = 1;
      }
   }
   
   if (included)
   {
      snprintf(line, sizeof(line), "java;");

      clean_class_name(cleaned_allocated_class_name, sizeof(cleaned_allocated_class_name), allocatedClassName);

      for (i = count - 1; i >= 0; i--)
      {
         char entry[1024];
         signature_string(jvmti, frames[i].method, entry, sizeof(entry));

         strcat(line, entry);
         strcat(line, ";");
      }

      snprintf(allocated_info, sizeof(allocated_info), "%s(%d) %d", cleaned_allocated_class_name, (jint)size,
               relative ? (jint)size : 1);

      strcat(line, allocated_info);

      mem_info_write_entry(file, line);

      if (statistics)
      {
         allo_count += 1;
         allo_total += size;

         (*jvmti)->GetFrameCount(jvmti, thread, &frame_count);
         if (frame_count > max_depth)
            max_depth = frame_count;
      }
   }
   
   (*jvmti)->Deallocate(jvmti, allocatedClassName);
}

static jvmtiError
enable_capabilities(jvmtiEnv *jvmti)
{
   jvmtiCapabilities capabilities;

   memset(&capabilities,0, sizeof(capabilities));
   capabilities.can_generate_vm_object_alloc_events = 1;

   // Request these capabilities for this JVM TI environment.
   return (*jvmti)->AddCapabilities(jvmti, &capabilities);
}

static jvmtiError
set_callbacks(jvmtiEnv *jvmti)
{
   jvmtiEventCallbacks callbacks;

   memset(&callbacks, 0, sizeof(callbacks));
   callbacks.VMObjectAlloc = &callbackVMObjectAlloc;
   return (*jvmti)->SetEventCallbacks(jvmti, &callbacks, (jint)sizeof(callbacks));
}

static void
option_delay(char* option)
{
   char* equal = strchr(option, '=');
   if (equal != NULL)
   {
      long d = atol(equal + 1);
      if (d > 0)
         delay = d;
   }
}

static void
option_duration(char* option)
{
   char* equal = strchr(option, '=');
   if (equal != NULL)
   {
      long d = atol(equal + 1);
      if (d > 0)
         duration = d;
   }
}

static void
option_depth(char* option)
{
   char* equal = strchr(option, '=');
   if (equal != NULL)
   {
      int d = atoi(equal + 1);
      if (d > 0 && d <= 20)
         depth = d;
   }
}

static void
option_statistics(char* option)
{
   statistics = strstr(option, "statistics") != NULL;
}

static void
option_relative(char* option)
{
   relative = strstr(option, "relative") != NULL;
}

static void
option_includes(char* option)
{
   int i = 0;
   int c = 0;
   char* equal = strchr(option, '=');

   if (equal != NULL)
   {
      char* c_settings = equal + 1;
      char* d_settings = strdup(c_settings);
      char* c_token = strtok(c_settings, ":");
      while (c_token != NULL)
      {
         c += 1;
         c_token = strtok(NULL, ":");
      }

      number_of_includes = c;
      includes = (char**)malloc(number_of_includes * sizeof(char*));
      
      char* d_token = strtok(d_settings, ":");
      while (d_token != NULL)
      {
         for (c = 0; c < strlen(d_token); c++)
         {
            if (d_token[c] == '.')
               d_token[c] = '/';
         }

         includes[i] = strdup(d_token);
         i++;
         d_token = strtok(NULL, ":");
      }

      free(d_settings);
   }
}

JNIEXPORT jint JNICALL 
Agent_OnLoad(JavaVM *vm, char *options, void *reserved)
{
   if (options != NULL)
   {
      char* token = strtok(options, ",");
      while (token != NULL)
      {
         if (strstr(token, "depth") != NULL)
         {
            option_depth(token);
         }
         else if (strstr(token, "statistics") != NULL)
         {
            option_statistics(token);
         }
         else if (strstr(token, "relative") != NULL)
         {
            option_relative(token);
         }
         else if (strstr(token, "includes") != NULL)
         {
            option_includes(token);
         }
         else if (strstr(token, "delay") != NULL)
         {
            option_delay(token);
         }
         else if (strstr(token, "duration") != NULL)
         {
            option_duration(token);
         }
         token = strtok(NULL, ",");
      }
   }

   file = mem_info_open(getpid());
   if (delay != 0 || duration != 0)
      start_time = get_time_ms();

   jvmtiEnv *jvmti;
   (*vm)->GetEnv(vm, (void **)&jvmti, JVMTI_VERSION_1);
   enable_capabilities(jvmti);
   set_callbacks(jvmti);

   (*jvmti)->SetEventNotificationMode(jvmti, JVMTI_ENABLE,
                                      JVMTI_EVENT_VM_OBJECT_ALLOC, (jthread)NULL);
   
   (*jvmti)->GenerateEvents(jvmti, JVMTI_EVENT_VM_OBJECT_ALLOC);

   return 0;
}

JNIEXPORT void JNICALL 
Agent_OnUnload(JavaVM *vm)
{
   int i = 0;

   if (includes)
   {
      for (i = 0; i < number_of_includes; i++)
         free(includes[i]);
      free(includes);
   }

   if (statistics)
   {
      printf("Allocation count: %ld\n", allo_count);
      printf("Total allocation: %ld\n", allo_total);
      printf("Max frame depth : %d\n", max_depth);
   }

   if (file)
   {
      mem_info_close(file);
      file = NULL;
   }
}
