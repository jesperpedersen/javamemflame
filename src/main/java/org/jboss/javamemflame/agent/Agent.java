/*
 * JVM agent to track memory allocations
 *
 * Copyright (C) 2018 Jesper Pedersen <jesper.pedersen@comcast.net>
 */
package org.jboss.javamemflame.agent;

import java.lang.instrument.Instrumentation;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.StringTokenizer;
import jdk.jfr.EventType;
import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;

import static java.time.temporal.ChronoUnit.MILLIS;

/**
 * Agent that generates an input file for flamegraph of memory allocations
 */
public class Agent
{
   private static FlightRecorder flightRecorder;
   private static Recording r;
   private static long delay;
   private static long duration;

   /**
    * Help
    */
   private static void help()
   {
      System.out.println("javamemflame: Recording flamegraph data for Java memory allocations");
      System.out.println("");
      System.out.println("Options:");
      System.out.println("  help       : Shows usage");
      System.out.println("  delay=X    : Delay recording by X ms");
      System.out.println("  duration=X : Record for X ms");
   }


   /**
    * Agent premain
    * @param args The arguments
    * @param inst The instrumentation
    */
   public static void premain(String args, Instrumentation inst)
   {
      try
      {
         boolean run = true;

         if (args != null && !"".equals(args.trim()))
         {
            StringTokenizer st = new StringTokenizer(args, ",");
            while (st.hasMoreTokens())
            {
               String token = st.nextToken();

               if ("help".equalsIgnoreCase(token))
               {
                  help();
                  run = false;
               }
               else if (token.startsWith("delay"))
               {
                  delay = Long.valueOf(token.substring(token.indexOf("=") + 1));
               }
               else if (token.startsWith("duration"))
               {
                  duration = Long.valueOf(token.substring(token.indexOf("=") + 1));
               }
            }
         }

         if (run)
         {
            if (FlightRecorder.isAvailable())
            {
               long pid = ProcessHandle.current().pid();
               Path path = Paths.get("javamemflame-" + pid + ".jfr");

               flightRecorder = FlightRecorder.getFlightRecorder();
               r = new Recording();

               for (EventType et : flightRecorder.getEventTypes())
               {
                  r.disable(et.getName());
               }

               r.enable​("jdk.ObjectAllocationInNewTLAB");
               r.enable​("jdk.ObjectAllocationOutsideTLAB");

               r.setDestination(path);
               r.setDumpOnExit(true);

               // We just start the recording; VMDeath will stop it automatically
               if (duration != 0)
               {
                  r.setDuration(Duration.of(duration, MILLIS));
               }

               r.scheduleStart(Duration.of(delay, MILLIS));
            }
            else
            {
               System.err.println("FlightRecorder is not available");
            }
         }
      }
      catch (Exception e)
      {
         System.err.println(e.getMessage());
         e.printStackTrace();
      }
   }
}
