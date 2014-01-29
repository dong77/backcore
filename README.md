Backcore
========

Mother project for core backend projects - to limit the number of private repositories. To learn more, visit http://coinport.github.io/backcore

You can build each project like this:

    cd f1
    gradle build

or:

    gradle :f1:build

You can also build all projects like this:

    gradle build

To add a new project, you need:

- creates project dir. such as: 'f1'
- creates setting file for the project. such as 'f1.settings.gradle'
- adds one item in file: setting.gradle
- creates build.gradle file in project(f1)

BTW, gradle enables you adding sub-projects to each project(f1)
