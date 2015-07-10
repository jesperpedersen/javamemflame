/*
 * libmemagent: JVM agent to track memory allocations
 *
 * Copyright (C) 2015 Jesper Pedersen <jesper.pedersen@comcast.net>
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
#include <string.h>

#include <sys/types.h>

#include <jni.h>
#include <jvmti.h>
#include <jvmticmlr.h>

#include "mem-info-file.h"

FILE *file = NULL;
int depth = 10;
int statistics = 0;
long total = 0;
int relative = 0;

void clean_class_name(char *dest, size_t dest_size, char *signature) {
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
   int i;

   char cleaned_allocated_class_name[1024];
   char allocated_info[1024];
   char line[2048];

   if (statistics)
      total += size;
   
   snprintf(line, sizeof(line), "java;");
   
   (*jvmti)->GetClassSignature(jvmti, object_klass, &allocatedClassName, NULL);

   clean_class_name(cleaned_allocated_class_name, sizeof(cleaned_allocated_class_name), allocatedClassName);

   (*jvmti)->GetStackTrace(jvmti, thread, (jint)0, (jint)depth, (jvmtiFrameInfo *)&frames, &count);

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
   
   (*jvmti)->Deallocate(jvmti, allocatedClassName);
}

jvmtiError
enable_capabilities(jvmtiEnv *jvmti)
{
   jvmtiCapabilities capabilities;

   memset(&capabilities,0, sizeof(capabilities));
   capabilities.can_generate_all_class_hook_events  = 1;
   capabilities.can_tag_objects                     = 1;
   capabilities.can_generate_object_free_events     = 1;
   capabilities.can_get_source_file_name            = 1;
   capabilities.can_get_line_numbers                = 1;
   capabilities.can_generate_vm_object_alloc_events = 1;
   capabilities.can_generate_compiled_method_load_events = 1;

   // Request these capabilities for this JVM TI environment.
   return (*jvmti)->AddCapabilities(jvmti, &capabilities);
}

jvmtiError
set_callbacks(jvmtiEnv *jvmti)
{
   jvmtiEventCallbacks callbacks;

   memset(&callbacks, 0, sizeof(callbacks));
   callbacks.VMObjectAlloc = &callbackVMObjectAlloc;
   return (*jvmti)->SetEventCallbacks(jvmti, &callbacks, (jint)sizeof(callbacks));
}

void
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

void
option_statistics(char* option)
{
   statistics = strstr(option, "statistics") != NULL;
}

void
option_relative(char* option)
{
   relative = strstr(option, "relative") != NULL;
}

JNIEXPORT jint JNICALL 
Agent_OnLoad(JavaVM *vm, char *options, void *reserved)
{
   if (options != NULL)
   {
      if (strchr(options, ',') != NULL)
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
            token = strtok(NULL, ",");
         }
      }
      else
      {
         if (strstr(options, "depth") != NULL)
         {
            option_depth(options);
         }
         else if (strstr(options, "statistics") != NULL)
         {
            option_statistics(options);
         }
         else if (strstr(options, "relative") != NULL)
         {
            option_relative(options);
         }
      }
   }

   file = mem_info_open(getpid());

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
   if (statistics)
      printf("Total allocation: %ld\n", total);

   mem_info_close(file);
   file = NULL;
}
