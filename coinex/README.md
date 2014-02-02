#coinex
I'm trying to use Akka's Persistence module to implement an exchange. Akka's Persistence module is based on the Eventsourced project.

The point of this prototype project is to learn if Akka cluster solution is better than a single machine solution.

##compile and run

In 3 terminals, run each of the following commands:

`./activator 'run 2551  "balance"'`
`./activator 'run 2552  "balance,market_1"'`
`./activator 'run 2553  "market_2,market_1"'`