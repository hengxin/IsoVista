import json
import os
import re
import subprocess
import zipfile

import pydot
from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse

app = FastAPI()
origins = [
    "http://localhost",
    "http://localhost:5173",
]

app.add_middleware(
    CORSMiddleware,
    allow_origins=origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

dbtest_path = "DBTest-1.0-SNAPSHOT-shaded.jar"
config_path = "config.properties"
result_path = "result"


class Bug:
    """
    Initializes the object with the given parameters.
    Args:
        bug_id (int): The ID of the bug.
        db_type (str): The type of the database.
        db_isolation (str): The isolation level of the database.
        checker_type (str): The type of the checker.
        checker_isolation (str): The isolation level of the checker.
        timestamp (int): The timestamp of the bug.
        bug_dir (str): The directory of the bug.
        config_path (str): The path of the config file.
        metadata_path (str): The path of the metadata file.
        log_path (str): The path of the log file.
    Returns:
        None
    """

    def __init__(self, bug_id, db_type, db_isolation, checker_type, checker_isolation, timestamp, bug_dir, config_path,
                 metadata_path, log_path):
        self.bug_id = bug_id
        self.db_type = db_type
        self.db_isolation = db_isolation
        self.checker_type = checker_type
        self.checker_isolation = checker_isolation
        self.timestamp = timestamp
        self.bug_dir = bug_dir
        self.hist_path = os.path.join(bug_dir, "bug_hist.txt")
        self.dot_path = os.path.join(bug_dir, "conflict.dot")
        self.config_path = config_path
        self.metadata_path = metadata_path
        self.log_path = log_path

    def zip(self, filename):
        file_paths = [self.config_path, self.metadata_path, self.log_path]
        for file in os.listdir(self.bug_dir):
            file_paths.append(os.path.join(self.bug_dir, file))
        with zipfile.ZipFile(filename, "w") as zip_file:
            for file_path in file_paths:
                file_name = os.path.basename(file_path)
                zip_file.write(file_path, arcname=file_name)


class BugStore:
    def __init__(self, directory):
        self.directory = directory
        self.bug_count = 0
        self.hist_count = 0
        self.bug_map = {}

    def scan(self):
        bug_id = 0
        for run_dir in os.listdir(self.directory):
            metadata_path = os.path.join(self.directory, run_dir, "metadata.json")
            config_path = os.path.join(self.directory, run_dir, "config.properties")
            log_path = os.path.join(self.directory, run_dir, "output.log")
            with open(metadata_path) as f:
                run_meta = json.load(f)
            self.bug_count += run_meta["bug_count"]
            self.hist_count += run_meta["history_count"]
            for bug_dir in os.listdir(os.path.join(self.directory, run_dir)):
                if not bug_dir.startswith("bug_"):
                    continue
                bug_id += 1
                self.bug_map[bug_id] = Bug(bug_id, run_meta["db_type"], run_meta["db_isolation"],
                                           run_meta["checker_type"], run_meta["checker_isolation"],
                                           run_meta["timestamp"], os.path.join(self.directory, run_dir, bug_dir),
                                           config_path, metadata_path, log_path)

    def get(self, bug_id):
        return self.bug_map[bug_id]


class Run:
    def __init__(self, run_id, db_type, db_isolation, checker_type, checker_isolation, timestamp, hist_count, bug_count, dir_path):
        self.run_id = run_id
        self.db_type = db_type
        self.db_isolation = db_isolation
        self.checker_type = checker_type
        self.checker_isolation = checker_isolation
        self.timestamp = timestamp
        self.hist_count = hist_count
        self.bug_count = bug_count
        self.dir_path = dir_path

    def zip(self, filename):
        with zipfile.ZipFile(filename, 'w', zipfile.ZIP_DEFLATED) as zipf:
            for root, dirs, files in os.walk(self.dir_path):
                for file in files:
                    file_path = os.path.join(root, file)
                    zipf.write(file_path, os.path.relpath(file_path, self.dir_path))


class RunStore:
    def __init__(self, directory):
        self.directory = directory
        self.buggy_run_count = 0
        self.run_count = 0
        self.run_map = {}

    def scan(self):
        run_id = 0
        for run_dir in os.listdir(self.directory):
            if not run_dir.startswith("run_"):
                continue
            self.run_count += 1
            run_id += 1

            metadata_path = os.path.join(self.directory, run_dir, "metadata.json")
            with open(metadata_path) as f:
                run_meta = json.load(f)

            self.run_map[run_id] = Run(run_id, run_meta["db_type"], run_meta["db_isolation"], run_meta["checker_type"],
                                       run_meta["checker_isolation"], run_meta["timestamp"],
                                       run_meta["history_count"], run_meta["bug_count"],
                                       os.path.join(self.directory, run_dir))

    def get(self, run_id):
        return self.run_map[run_id]


bug_store = BugStore(result_path)
bug_store.scan()
run_store = RunStore(result_path)
run_store.scan()

@app.get("/history_count")
async def get_history_count():
    return bug_store.hist_count


@app.get("/bug_count")
async def get_bug_count():
    return bug_store.bug_count


@app.post("/run")
async def run(request: Request):
    params = await request.json()

    # write config.properties
    config = ''
    for key, value in params.items():
        option = key.replace('_', '.')
        config += f'{option}={value}\n'
    with open(config_path, 'w') as file:
        file.write(config)

    # run
    subprocess.run(["java", "-jar", dbtest_path, config_path])

    # update data
    bug_store.scan()
    run_store.scan()
    return {}


@app.get("/bug_list")
async def get_bug_list():
    return list(bug_store.bug_map.values())

@app.get("/run_list")
async def get_run_list():
    return list(run_store.run_map.values())


@app.get("/view/{bug_id}")
async def view_bug(bug_id: int):
    bug = bug_store.get(bug_id)
    graphs = pydot.graph_from_dot_file(bug.dot_path)
    nodes = []
    edges = []
    for node in graphs[0].get_nodes():
        nodes.append({
            "id": node.get_name().replace("\"", ""),
            "label": node.get_name().replace("\"", ""),
            "ops": re.sub(r"transaction=Transaction\(id=\d+\), ", "",
                          node.get("ops").replace("\"", "").replace("), Operation", ")\nOperation")).replace("[","").replace("]", ""),
        })

    for edge in graphs[0].get_edges():
        edges.append({
            "source": edge.get_source().replace("\"", ""),
            "target": edge.get_destination().replace("\"", ""),
            "label": edge.get("label").replace("\\n", " ").replace("\"", ""),
        })

    return {"nodes": nodes, "edges": edges}


@app.get("/download/{bug_id}")
async def download_bug(bug_id: int):
    zip_file = "download.zip"
    bug_store.get(bug_id).zip(zip_file)
    return FileResponse(zip_file, filename=zip_file)

@app.get("/download_run/{run_id}")
async def download_bug(run_id: int):
    zip_file = "download.zip"
    run_store.get(run_id).zip(zip_file)
    return FileResponse(zip_file, filename=zip_file)
