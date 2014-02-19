#README

这个小程序demo了Akka的Persistence模块是如何做到EventSourcing的。

首先，你必须编译一个依赖包，这个依赖包把所有event持久化到本地mongodb中。

```
git clone git@github.com:dong77/akka-persistence-mongo.git
cd akka-persistence-mongo
sbt publishLocal
cd ..
```

然后启动mongodb。

`./mongod`

然后，安装bitcoinj 0.11

```
git clone https://code.google.com/p/bitcoinj/ bitcoinj
cd bitcoinj
git fetch --all
git checkout 410d4547a7dd20745f637313ed54d04d08d28687
mvn install
cd ..
```

然后checkout这个repo：

```
git clone https://github.com/dong77/coinex
cd coinex
git checkout eventsourcing-demo
```

在src/main/resources/coinex.conf中，配置了mongodb的数据库和collection名称，默认是：

`casbah-journal.mongo-url = "mongodb://127.0.0.1:27017/coinex.journals"`


然后你可以运行app：

`./activator 'run 2551'`

你可以用不同的端口启动几个程序，他们会自动加入到一个cluster中，如果2551挂掉，其他程序会自动backup，把cluster中唯一的processor再启动起来，并且恢复状态。

`./activator 'run 2552'`
`./activator 'run 2553'`

但你必须先启动2551。

每一次重启，程序都会生成新的event。如果你只想看从其之前的event replay，注释掉 KioApp.scala 中`Thread.sleep`之后的内容。

另外cluster中的每个节点都会启动一个View，这个View也会接收到所有的event，并且建立自己的内存状态，用来回答可能Query。在这个demo中，view只是打印了接收到的命令。

你可以试着杀掉2551进程，然后会看到另一个进程中会的processor会被初始化: `core processor created`.

如果mongodb无法连接，请更改mongodb的配置，把“bind_ip”注释掉。


##Akka和Persistence模块
具体可以参考：http://doc.akka.io/docs/akka/snapshot/scala/persistence.html

以前有个Eventsourced的开源项目，项目的负责人现在和Akka团队合并了，就有了现在的Persistence模块。用起来更加顺畅了。

现在这个模块还是实验阶段，在Akka 2.3.0-RC2中。

##挑战
下面是一些挑战，如果我们用Akka Persistence

- 这个模块还不成熟
- 我们需要弄明白每个event如何被序列化的
- 我们需要弄清楚event写数据库的逻辑
- 没有尝试过snapshot，也不知道snapshot过程中程序对外是否能保持可用状态
- 内存状态维护没有经验，不知道怎样尽量降低内存消耗