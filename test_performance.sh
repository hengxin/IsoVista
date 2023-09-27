#!/bin/bash

rm result/runtime.csv
touch result/runtime.csv
echo "sessions,duration(ms),memory(MB)" >> result/runtime.csv

sessions=(2 5 10 20 30 50)

for session in "${sessions[@]}"; do

  sed -i "s/workload.session=[0-9]*/workload.session=$session/" config.properties

  java -jar target/DBTest-1.0-SNAPSHOT-shaded.jar config.properties

done

echo "All tests finished!"