libjavamemflame will generate a `mem-info-<pid>.txt` file with Java memory allocation information which can be turned into a flame graph.

## Requirements

* [http://github.com/brendangregg/FlameGraph](http://github.com/brendangregg/FlameGraph "FlameGraph")

## Build

	cmake .
	make

## Usage

libjavamemflame will record memory allocation during the lifetime of the JVM with

	java -agentpath:/path/to/libjavamemflame.so ...

Then the information can be turned into a flame graph using

	/path/to/FlameGraph/flamegraph.pl --color=java mem-info-<pid>.txt > javamem.svg

The result `javamem.svg` can be viewed with any SVG viewer, like Firefox.

## Thanks to

* [Johannes Rudolph](http://github.com/jrudolph "Johannes Rudolph")
* [Brendan Gregg](http://github.com/brendangregg "Brendan Gregg")

## License

This project is licensed under GPL v2. See the LICENSE file.
