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

#include <sys/types.h>
#include <stdio.h>

#include <error.h>
#include <errno.h>

#include "mem-info-file.h"

FILE*
mem_info_open(pid_t pid)
{
   char filename[500];

   snprintf(filename, sizeof(filename), "mem-info-%d.txt", pid);
   FILE* res = fopen(filename, "w");

   if (!res)
      error(0, errno, "Couldn't open %s.", filename);

   return res;
}

int
mem_info_close(FILE* file)
{
   if (file)
      return fclose(file);
   else
      return 0;
}

void
mem_info_write_entry(FILE* file, const char* line)
{
   if (file)
      fprintf(file, "%s\n", line);
}
