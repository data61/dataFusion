# dataFusion-common

## Introduction

Common [Scala](http://scala-lang.org/) code shared by the other Scala sub-projects.

This module provides:

- shared JSON data structures; and
- a simple parallel processing framework used by all dataFusion’s multi-threaded CLI’s.

The parallel processing framework provides the following:

- an input queue of items of some type I;
- an output queue of items of some type O;
- an input [thread](https://docs.oracle.com/javase/8/docs/api/java/lang/Thread.html) that reads the input, generating items of type I and adding them to the input queue;
- a number of worker threads (generally equal to the number of available CPUs), each independently taking an I from the input queue, performing useful work to produce an O and adding this to the output queue;
- an output thread that takes an O from the output queue and writes the output.


Parameters to the framework are:

- the types I and O;
- the transformation from input to I;
- the transformation from I to O, that is the work to be performed in parallel to the maximum extent possible;
- the transformation from O to output.


All the dataFusion muti-threaded CLI’s operate by defining these parameters to suit the task at hand and using this framework.


## Build

See the top level README.
