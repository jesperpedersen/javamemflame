/*
 * JVM agent to track memory allocations
 *
 * Copyright (C) 2019 Jesper Pedersen <jesper.pedersen@comcast.net>
 */
package org.jboss.javamemflame;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.consumer.RecordedStackTrace;

/**
 * Frame
 */
class Frame
{
   RecordedStackTrace rst;
   RecordedClass rc;

   Frame(RecordedStackTrace rst, RecordedClass rc)
   {
      this.rst = rst;
      this.rc = rc;
   }

   /**
    * Get stacktrace
    * @return The value
    */
   RecordedStackTrace getRecordedStackTrace()
   {
      return rst;
   }

   /**
    * Get class
    * @return The value
    */
   RecordedClass getRecordedClass()
   {
      return rc;
   }

   /**
    * Should include
    * @param includes The includes
    * @return True if include, otherwise false
    */
   boolean shouldInclude(Set<String> includes)
   {
      if (includes == null || includes.size() == 0)
         return true;

      String str = toString();
      for (String include : includes)
      {
         if (str.contains(include))
         {
            return true;
         }
      }

      return false;
   }

   /**
    * Translate from byte code name to human readable name
    * @param input The input
    * @return Human readable
    */
   private String translate(String input)
   {
      int array = 0;
      int i = 0;

      StringBuilder sb = new StringBuilder();
   
      while (input.charAt(i) == '[')
      {
         array++;
         i++;
      }
   
      if (input.charAt(i) == 'Z')
      {
         sb.append("boolean");
      }
      else if (input.charAt(i) == 'B')
      {
         sb.append("byte");
      }
      else if (input.charAt(i) == 'C')
      {
         sb.append("char");
      }
      else if (input.charAt(i) == 'D')
      {
         sb.append("double");
      }
      else if (input.charAt(i) == 'F')
      {
         sb.append("float");
      }
      else if (input.charAt(i) == 'I')
      {
         sb.append("int");
      }
      else if (input.charAt(i) == 'J')
      {
         sb.append("long");
      }
      else if (input.charAt(i) == 'S')
      {
         sb.append("short");
      }
      else if (input.charAt(i) == 'L')
      {
         sb.append(input.substring(i + 1, input.length() - 1));
      }
      else
      {
         sb.append(input.substring(i));
      }

      for (int array_counter = 0; array_counter < array; array_counter++)
      {
         sb.append("[]");
      }
   
      return sb.toString();
   }

   /**
    * Hash code
    */
   @Override
   public int hashCode()
   {
      int hc = 41;
      hc += 7 * rst.hashCode();
      hc += 7 * rc.hashCode();
      return hc;
   }

   /**
    * Equals
    */
   @Override
   public boolean equals(Object o)
   {
      if (o == this)
         return true;

      if (o == null || !(o instanceof Frame))
         return false;

      Frame f = (Frame)o;

      if (!rst.equals(f.getRecordedStackTrace()))
         return false;
      if (!rc.equals(f.getRecordedClass()))
         return false;

      return true;
   }

   /**
    * toString
    */
   @Override
   public String toString()
   {
      StringBuilder sb = new StringBuilder();
      sb.append("java;");

      int size = rst.getFrames().size();
      for (int i = size - 1; i >= 0; i--)
      {
         RecordedFrame irf = rst.getFrames().get(i);
         RecordedMethod irm = irf.getMethod();
         RecordedClass irc = irm.getType();
         sb.append(irc.getName().replace('.', '/'));
         sb.append(":.");
         sb.append(irm.getName());
         sb.append(";");
      }

      sb.append(translate(rc.getName()));

      return sb.toString();
   }
}
