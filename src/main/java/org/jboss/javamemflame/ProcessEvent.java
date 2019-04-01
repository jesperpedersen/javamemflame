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
import jdk.jfr.consumer.RecordedStackTrace;

/**
 * Process event
 */
class ProcessEvent implements Runnable
{
   ConcurrentMap<Frame, AtomicLong> allocs;
   Set<String> includes;
   boolean size;
   RecordedEvent re;

   ProcessEvent(ConcurrentMap<Frame, AtomicLong> allocs, Set<String> includes, boolean size, RecordedEvent re)
   {
      this.allocs = allocs;
      this.includes = includes;
      this.size = size;
      this.re = re;
   }

   /**
    * Process
    */
   public void run()
   {
      String eventName = re.getEventType().getName();

      if ("jdk.ObjectAllocationInNewTLAB".equals(eventName) ||
          "jdk.ObjectAllocationOutsideTLAB".equals(eventName))
      {
         if (re.hasField("stackTrace") && re.hasField("objectClass") && re.hasField("allocationSize"))
         {
            RecordedStackTrace st = (RecordedStackTrace)re.getValue("stackTrace");
            RecordedClass rc = (RecordedClass)re.getValue("objectClass");

            if (st != null && rc != null)
            {
               Frame frame = new Frame(st, rc);

               if (frame.shouldInclude(includes))
               {
                  AtomicLong alloc = allocs.get(frame);
                  if (alloc == null)
                  {
                     AtomicLong newAlloc = new AtomicLong(0);

                     alloc = allocs.putIfAbsent(frame, newAlloc);
                     if (alloc == null)
                     {
                        alloc = newAlloc;
                     }
                  }

                  if (size)
                  {
                     alloc.addAndGet(re.getLong("allocationSize"));
                  }
                  else
                  {
                     alloc.addAndGet(1);
                  }
               }
            }
         }
      }
   }
}
