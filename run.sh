#!/bin/bash

# Start the frontend process
http-server dist &

# Start the backend process
python3 main.py &

wait -n

# Exit with status of process that exited first
exit $?