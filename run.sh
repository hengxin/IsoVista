#!/bin/bash

# Check if the environment variable VITE_BACKEND_URL is set
if [ -z "${VITE_BACKEND_URL}" ]; then
    echo "VITE_BACKEND_URL is not set. Default to http://127.0.0.1:8000/"
else
    # Replace the backend url with VITE_BACKEND_URL
    echo "VITE_BACKEND_URL is set to ${VITE_BACKEND_URL}."

    # Find and replace in index-*.js files within /app/dist/asserts/
    find /app/dist/assets/ -type f -name 'index-*.js' -exec sed -i "s|http://127.0.0.1:8000/|$VITE_BACKEND_URL|g" {} \;

    echo "Backend url replacement complete."
fi


# Start the frontend process
http-server dist &

# Start the backend process
python3 main.py &

wait -n

# Exit with status of process that exited first
exit $?