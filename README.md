javamemflame will generate a flame graph with Java memory allocation information.

## Requirements

* Java 11
* Apache Maven

## Build

```sh
mvn clean package
```

## Usage

javamemflame will record memory allocation during the lifetime of the JVM with

```sh
java -javaagent:/path/to/javamemflame.jar ...
```

Then the information can be turned into a flame graph using

```sh
java -jar /path/to/javamemflame.jar javamemflame-pid.jfr
```

The result `javamemflame-pid.svg` can be viewed with any SVG viewer, like Firefox.

## Agent options

The following agent options are supported.

* `delay=<number>`: Delays the recording by the specified number of milliseconds.
* `duration=<number>`: Record by the specified number of milliseconds.

Multiple options can be selected using the ',' character, like

```sh
java -javaagent:/path/to/javamemflame.jar=duration=1000,delay=500 ...
```

## Main options

### Output format

The output format of javamemflame can be controlled by

```sh
java -jar javamemflame.jar -o svg javamemflame-pid.jfr
```

Supported values

* `svg`: Flame graph
* `txt`: Text file

The text file can be turned into a flame graph using [FlameGraph](https://github.com/brendangregg/FlameGraph "FlameGraph")

```sh
/path/to/FlameGraph/flamegraph.pl --flamechart --color=java javamemflame-pid.txt > javamemflame-pid.svg
```

### Threads

javamemflame can use multiple threads to process the .jfr file faster by

```sh
java -jar javamemflame.jar -t 8 javamemflame-pid.jfr
```

### Cut off

javamemflame can filter out allocations under the specified number, and merge them together
under a `Filtered` category. F.ex. cut off at 100 MB

```sh
java -jar javamemflame.jar -c 100000000 javamemflame-pid.jfr
```

### Title

The flame graph can be given a title using

```sh
java -jar javamemflame.jar --title "My FlameGraph" javamemflame-pid.jfr
```

### Count

The number of allocation counts, instead of their combined size, can be done using

```sh
java -jar javamemflame.jar -n javamemflame-pid.jfr
```

### JFR files

Multiple .jfr files can be specified on the command line and their information will be merged

```sh
java -jar javamemflame.jar javamemflame-pid1.jfr javamemflame-pid2.jfr
```

### Package filtering

javamemflame can filter on package names, and only include their information

```sh
java -jar javamemflame.jar javamemflame-pid.jfr package[,package]*
```

## Thanks to

* [Brendan Gregg](http://github.com/brendangregg "Brendan Gregg")

## License

This project is licensed under EPL v2. See the LICENSE file.
