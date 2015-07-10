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

## Options

The following options are supported.

`depth=<number>`: Selects the depth level of recorded stack frames. Must be between 1 and 20. Default is 10.

`statistics`: Outputs statistics at JVM termination.

`relative`: Adjusts the sample size to be relative to the allocation size. Useful for finding few big allocations.

Multiple options can be selected using the ',' characters, like

	java -agentpath:/path/to/libjavamemflame.so=depth=8,statistics ...

## Thanks to

* [Johannes Rudolph](http://github.com/jrudolph "Johannes Rudolph")
* [Brendan Gregg](http://github.com/brendangregg "Brendan Gregg")

## License

This project is licensed under GPL v2. See the LICENSE file.
