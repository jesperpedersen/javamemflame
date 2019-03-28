/*
 * JVM agent to track memory allocations
 *
 * Copyright (C) 2019 Jesper Pedersen <jesper.pedersen@comcast.net>
 */
package org.jboss.javamemflame;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordingFile;

/**
 * Main class that creates the meminfo-pid.txt file
 */
public class Main
{
   /**
    * Open a file
    * @param p The path of the file
    * @return The file
    */
   private static BufferedWriter openFile(Path p) throws Exception
   {
      BufferedWriter bw = Files.newBufferedWriter(p,
                                                  StandardOpenOption.CREATE,
                                                  StandardOpenOption.WRITE,
                                                  StandardOpenOption.TRUNCATE_EXISTING);
      return bw;
   }

   /**
    * Append data to a file
    * @param bw The file
    * @param s The string
    */
   private static void append(BufferedWriter bw, String s) throws Exception
   {
      bw.write(s, 0, s.length());
      bw.newLine();
   }

   /**
    * Close a file
    * @param bw The file
    */
   private static void closeFile(BufferedWriter bw) throws Exception
   {
      bw.flush();
      bw.close();
   }

   /**
    * Translate from byte code name to human readable name
    * @param input The input
    * @return Human readable
    */
   private static String translate(String input)
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
         sb.append(input.substring(i + 1));
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
    * Sort the map by value
    * @param m The unsorted map
    * @return The sorted map
    */
   private static Map<String, AtomicLong> sortByValue(Map<String, AtomicLong> m)
   {
      List<Map.Entry<String, AtomicLong>> l = new LinkedList<>(m.entrySet());

      Collections.sort(l, new Comparator<Map.Entry<String, AtomicLong>>()
      {
         public int compare(Map.Entry<String, AtomicLong> o1, Map.Entry<String, AtomicLong> o2)
         {
            long l1 = o1.getValue().get();
            long l2 = o2.getValue().get();

            if (l2 > l1)
            {
               return 1;
            }
            else if (l2 < l1)
            {
               return -1;
            }

            return 0;
         }
      });

      Map<String, AtomicLong> sorted = new LinkedHashMap<>();
      for (Map.Entry<String, AtomicLong> entry : l)
      {
         sorted.put(entry.getKey(), entry.getValue());
      }

      return sorted;
   }

   /**
    * main
    * @parameter args The program arguments
    */
   public static void main(String[] args)
   {
      try
      {
         if (args == null || args.length < 1)
         {
            System.out.println("javamemflame: Recording flamegraph data for Java memory allocations");
            System.out.println("");
            System.out.println("Usage: java -jar javamemflame.jar [-t num] <file_name> [include[,include]*]");
            return;
         }

         int i = 0;
         int threads = 1;

         if ("-t".equals(args[i]))
         {
            i++;
            threads = Integer.valueOf(args[i]);
            i++;
         }

         String file = args[i];
         Path path = Paths.get(file);
         long pid = 0;
         Set<String> includes = null;
         ConcurrentMap<String, AtomicLong> allocs = new ConcurrentHashMap<>();

         if (file.indexOf("-") != -1 && file.indexOf(".") != -1)
            pid = Long.valueOf(file.substring(file.indexOf("-") + 1, file.indexOf(".")));

         BufferedWriter writer = openFile(Paths.get("mem-info-" + pid + ".txt"));

         if (args.length > i + 1)
         {
            includes = new HashSet<>();

            StringTokenizer st = new StringTokenizer(args[i + 1], ",");
            while (st.hasMoreTokens())
            {
               String include = st.nextToken();
               include = include.replace('.', '/');
               includes.add(include);
            }
         }

         RecordingFile rcf = new RecordingFile​(path);

         if (threads > 1)
         {
            ExecutorService es = Executors.newFixedThreadPool(threads);

            while (rcf.hasMoreEvents())
            {
               RecordedEvent re = rcf.readEvent();
               ProcessEvent pe = new ProcessEvent(allocs, includes, re);
               es.submit(pe);
            }

            es.shutdown();
            es.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
         }
         else
         {
            while (rcf.hasMoreEvents())
            {
               RecordedEvent re = rcf.readEvent();
               ProcessEvent pe = new ProcessEvent(allocs, includes, re);
               pe.run();
            }
         }

         for (Map.Entry<String, AtomicLong> entry : sortByValue(allocs).entrySet())
         {
            append(writer, entry.getKey() + " " + entry.getValue());
         }

         rcf.close();
         closeFile(writer);
      }
      catch (Exception e)
      {
         System.err.println(e.getMessage());
         e.printStackTrace();
      }
   }

   /**
    * Process event
    */
   static class ProcessEvent implements Runnable
   {
      ConcurrentMap<String, AtomicLong> allocs;
      Set<String> includes;
      RecordedEvent re;

      ProcessEvent(ConcurrentMap<String, AtomicLong> allocs, Set<String> includes, RecordedEvent re)
      {
         this.allocs = allocs;
         this.includes = includes;
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

               if (st != null)
               {
                  List<RecordedFrame> lrf = st.getFrames();
                  if (lrf != null && lrf.size() > 0)
                  {
                     StringBuilder sb = new StringBuilder();
                     sb.append("java;");

                     for (int i = lrf.size() - 1; i >= 0; i--)
                     {
                        RecordedFrame rf = st.getFrames().get(i);
                        RecordedMethod rm = rf.getMethod();
                        RecordedClass rc = rm.getType();
                        sb.append(rc.getName().replace('.', '/'));
                        sb.append(":.");
                        sb.append(rm.getName());
                        sb.append(";");
                     }

                     RecordedClass rc = (RecordedClass)re.getValue("objectClass");
                     sb.append(translate(rc.getName()));

                     String entry = sb.toString();

                     if (includes == null)
                     {
                        AtomicLong alloc = allocs.get(entry);
                        if (alloc == null)
                        {
                           AtomicLong newAlloc = new AtomicLong(0);

                           alloc = allocs.putIfAbsent(entry, newAlloc);
                           if (alloc == null)
                           {
                              alloc = newAlloc;
                           }
                        }

                        alloc.addAndGet(re.getLong("allocationSize"));
                     }
                     else
                     {
                        for (String include : includes)
                        {
                           if (entry.contains(include))
                           {
                              AtomicLong alloc = allocs.get(entry);
                              if (alloc == null)
                              {
                                 AtomicLong newAlloc = new AtomicLong(0);

                                 alloc = allocs.putIfAbsent(entry, newAlloc);
                                 if (alloc == null)
                                 {
                                    alloc = newAlloc;
                                 }
                              }

                              alloc.addAndGet(re.getLong("allocationSize"));
                              break;
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }
}
