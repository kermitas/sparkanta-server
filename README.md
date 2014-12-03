#sparkanta-server
Sparkanta server (Scala+Akka) code that will communicate with sparkanta-client.

## Usage:
Different ways are designed to be used for different purposes:

  - use API as dependency:
    
```scala
libraryDependencies += "as.sparkanta" %% "sparkanta-api" % "xx.yy.zz-SNAPSHOT" // instead of 'xx.yy.zz-SNAPSHOT' please use latest version from project/Build.scala
```

  - standalone use:
    - generate distribution, see below *Grabbing all JARs, generating distribution, run*,
    - generate runnable fat-jar, see below *Generating runnable far-jar, run*.

## SBT:
#### Project structure:
This is SBT multi-project. Command executed in one project will be forwarded down to its all sub-projects.

The top most project in hierarchy is *sparkanta* and it is placed in root folder (it has no sources).

#### Compile:
- clone into your local drive: `git clone https://github.com/kermitas/sparkanta-server.git`
- go to root folder (`cd sparkanta-server`)
- start SBT `sbt`
- to compile:
    - execute `compile` command
    - to work in continuous compilation use `~compile`
    - **the most preferred way is to always compile with tests** `~test:compile` 

#### Run:
- execute `run` in *sparkanta-server* project to run server

#### Cross compilation:
- many commands (like `compile` or `publish-local`) prefixed with '+' will perform cross compilation

#### Unit tests:
- `test` command will execute unit tests

#### IntelliJ Idea project:
- if you are using [IntelliJ Idea](http://www.jetbrains.com/idea/) you can generate Idea project using `gen-idea` command

#### Publish artifact:
- publishing into your local repository `publish-local` (I recommend `+publish-local`)

#### Grabbing all JARs, generating distribution, run:
- you can use `pack` command to generate distribution with all JAR files
- generated distribution will be in `target/pack`
    - look out: distribution will be generated only for projects that have main class set and can be run
- if you want to run it:
    - first be sure that there is distribution folder `target/pack`, if there is no that means that this project can not be run, if yes change to this folder `cd target/pack`
    - run it be executing one of scripts in `bin` folder (for example: by executing `bin/run`)

#### Generating runnable far-jar, run:
- `assembly` command will generate runnable fat-jar (look out: this option will work only for projects that have main class set and can be run)
- produced runnable fat-jar should be in `target/scala-x.y/` (file name should be similar to *sparkanta-server-xxx-yyy-zzz-assembly-0.1.0-SNAPSHOT.jar*)
- if you want to run it (only UI can be run): 
    - execute command like `java -jar sparkanta-server-xxx-yyy-zzz-assembly-0.1.0-SNAPSHOT.jar`

#### Statistics:
- `stats` command will print statistics (like number of Scala files, Java files, number of lines etc.)

#### Dependency graph:
- project is using *sbt-dependency-graph* SBT plugin, execute `dependency-graph-ml` command to generate .graphml files in all *target* directories of all sub-projects (you can use [yEd](http://www.yworks.com/en/products_yfiles_about.html) to open them, then choose *Layout > Hierarchical*)

#### Code formatting:
- code format is guarded by [Scalariform](https://github.com/daniel-trinh/scalariform), formatting is done before each compilation
- formatting rules are consistent for all projects of this SBT multi-project and are defined in `project/ScalariformSettings.scala`
- no unformatted code is allowed in this project

## GIT (source repository)
This repository works in branching model described in "[A successful Git branching model](http://nvie.com/posts/a-successful-git-branching-model/)".
