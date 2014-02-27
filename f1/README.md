F1
========

The project to process business logic of exchange platform within one thread.

The design doc: https://github.com/coinport/documents/blob/master/Exchange/design/cqrs_based_design.md

Run:

gradle run -Pmain=0 (gradle run)  // perf test
gradle run -Pmain=1  // listen to the redis queue
