#!/bin/sh

javac ticketingsystem/Verify.java -d ./bin

java -cp bin ticketingsystem/Verify > trace 
java -jar verify.jar trace