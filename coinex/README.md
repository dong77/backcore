#coinex
I'm trying to use Akka's Persistence module to implement an exchange. Akka's Persistence module is based on the Eventsourced project.

The point of this prototype project is to learn if Akka cluster solution is better than a single machine solution.

##compile and run

First you need to build another project:

`git clone git@github.com:dong77/akka-persistence-mongo.git`
`cd akka-persistence-mongo`
`sbt publishLocal`

you need to run mongodb first as all events are logged into mongodb, be sure to use the default port:

`./mongod`


In 2 terminals, run each of the following commands:

`./activator 'run 2551  "f,bp,tp"'`

`./activator 'run 2552  "f,bp,tp"'`



In the first terminal, you will see something like this `====== balances: 2 -> 40.0`, then if you kill the app in the first terminal, you will see the something in the second terminal. 

## what I did in this app?
It's very simple actually, please read the code.


##possible future todos

- local deployment working using Chef and Vagrant
- cluster integration tests using the Multi Node Test utilities
- continuous integration with Cloudbees/Jenkins attached to Github repo
