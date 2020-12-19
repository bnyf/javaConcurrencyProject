#!/bin/sh

## compile Trace.java and put .class into bin
javac -encoding UTF-8 -cp . ticketingsystem/Check.java

result=1

## begin test
for i in $(seq 1 50); do ## you can change the number of test, default is 50
    java -cp . ticketingsystem/Check > trace 
    java -jar checker.jar --no-path-info --coach 10 --seat 100 --station 10 < trace
    if [ $? != 0 ]; then
        echo "Test failed!!! see trace file to debug"
        result=0
        break
    fi
done

if [ $result == 1 ]; then
    echo "Test passed!!!"
fi
