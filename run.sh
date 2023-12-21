#!/bin/bash

# Start the frontend process
http-server dist &

# Start the backend process
uvicorn main:app --host 0.0.0.0 --port 8000 &

wait -n

# Exit with status of process that exited first
exit $?