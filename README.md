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

`includes=<package>[:<package>]*`: Define the package(s) that should be included in the recorded information within the
stack frame depth. Alternative is to use tools like `grep` after the run. Default is all packages.

`delay=<number>`: Delays the recording by the specified number of milliseconds.

`duration=<number>`: Record by the specified number of milliseconds.

Multiple options can be selected using the ',' character, like

	java -agentpath:/path/to/libjavamemflame.so=depth=8,includes=bar.foo.pkg1:bar.foo.pkg2 ...

## Tricks

The `mem-info-<pid>.txt` file can get quite huge in size, and therefore the resulting flame graph as well.

However, the `mem-info-<pid>.txt` file is in a text format, so standard tools can be used to
find the information needed.

Show the top 10 allocation traces

<pre>
sort mem-info-&lt;pid&gt;.txt | uniq -c | sort -nr | head -10
</pre>

## Thanks to

* [Johannes Rudolph](http://github.com/jrudolph "Johannes Rudolph")
* [Brendan Gregg](http://github.com/brendangregg "Brendan Gregg")

## License

This project is licensed under GPL v2. See the LICENSE file.
