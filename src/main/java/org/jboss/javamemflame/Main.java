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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
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
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;

/**
 * Main class that creates the meminfo-pid.txt file
 */
public class Main
{
   /**
    * Sort the map by value
    * @param m The unsorted map
    * @return The sorted map
    */
   private static Map<String, Long> sortByValue(Map<String, Long> m)
   {
      List<Map.Entry<String, Long>> l = new LinkedList<>(m.entrySet());

      Collections.sort(l, new Comparator<Map.Entry<String, Long>>()
      {
         public int compare(Map.Entry<String, Long> o1, Map.Entry<String, Long> o2)
         {
            long l1 = o1.getValue().longValue();
            long l2 = o2.getValue().longValue();

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

      Map<String, Long> sorted = new LinkedHashMap<>();
      for (Map.Entry<String, Long> entry : l)
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
            System.out.println("Usage: java -jar javamemflame.jar [-o svg|txt] [--title text] [-n] [-t num] <file_name> [include[,include]*]");
            return;
         }

         int i = 0;
         boolean svg = true;
         String title = "Flamegraph";
         int threads = 1;
         boolean size = true;
         int cutoff = 0;
         long filtered = 0;
         long pid = 0;
         Set<String> includes = null;
         ConcurrentMap<Frame, AtomicLong> allocs = new ConcurrentHashMap<>();
         List<Path> paths = new ArrayList<>();

         for (i = 0; i < args.length; i++)
         {
            if ("-n".equals(args[i]))
            {
               size = false;
            }
            else if ("-c".equals(args[i]))
            {
               i++;
               cutoff = Integer.valueOf(args[i]);
            }
            else if ("-t".equals(args[i]))
            {
               i++;
               threads = Integer.valueOf(args[i]);
            }
            else if ("-o".equals(args[i]))
            {
               i++;
               svg = "svg".equals(args[i]);
            }
            else if ("--title".equals(args[i]))
            {
               i++;
               title = args[i];
            }
            else if (args[i].endsWith(".jfr"))
            {
               Path path = Paths.get(args[i]);
               paths.add(path);
            }
            else
            {
               if (includes == null)
                  includes = new HashSet<>();

               StringTokenizer st = new StringTokenizer(args[i], ",");
               while (st.hasMoreTokens())
               {
                  String include = st.nextToken();
                  include = include.replace('.', '/');
                  includes.add(include);
               }
            }
         }

         if (paths.size() == 0)
         {
            System.out.println("javamemflame: No .jfr files specified");
            return;
         }
         else if (paths.size() == 1)
         {
            String file = paths.get(0).toFile().getName();
            if (file.indexOf("-") != -1 && file.indexOf(".") != -1)
               pid = Long.valueOf(file.substring(file.indexOf("-") + 1, file.indexOf(".")));
         }

         BufferedWriter writer;
         if (svg)
         {
            writer = TextFile.openFile(Paths.get("javamemflame-" + pid + ".svg"));
         }
         else
         {
            writer = TextFile.openFile(Paths.get("javamemflame-" + pid + ".txt"));
         }

         for (Path path : paths)
         {
            RecordingFile rcf = new RecordingFile​(path);

            if (threads > 1)
            {
               ExecutorService es = Executors.newFixedThreadPool(threads);

               while (rcf.hasMoreEvents())
               {
                  RecordedEvent re = rcf.readEvent();
                  ProcessEvent pe = new ProcessEvent(allocs, includes, size, re);
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
                  ProcessEvent pe = new ProcessEvent(allocs, includes, size, re);
                  pe.run();
               }
            }

            rcf.close();
         }

         Map<String, Long> folded = new HashMap<>();
         for (Map.Entry<Frame, AtomicLong> entry : allocs.entrySet())
         {
            String key = entry.getKey().toString();
            Long l = folded.get(key);

            if (l == null)
               l = Long.valueOf(0);

            l = Long.valueOf(l.longValue() + entry.getValue().get());
            folded.put(key, l);
         }

         List<String> svgData = new ArrayList<>();
         for (Map.Entry<String, Long> entry : sortByValue(folded).entrySet())
         {
            long value = entry.getValue().longValue();

            if (value >= cutoff)
            {
               String s = entry.getKey() + " " + value;
               if (svg)
               {
                  svgData.add(s);
               }
               else
               {
                  TextFile.append(writer, s);
               }
            }
            else
            {
               filtered += value;
            }
         }

         if (filtered > 0)
         {
            StringBuilder sb = new StringBuilder();
            sb.append("java;Filtered ");
            sb.append(filtered);

            String s = sb.toString();
            if (svg)
            {
               svgData.add(s);
            }
            else
            {
               TextFile.append(writer, s);
            }
         }

         if (svg)
         {
            Flamegraph flamegraph = new Flamegraph(title, svgData);
            flamegraph.write(writer);
         }
         
         TextFile.closeFile(writer);
      }
      catch (Exception e)
      {
         System.err.println(e.getMessage());
         e.printStackTrace();
      }
   }
}
