import request from '@/utils/request'

const historyCountUrl = "/history_count"

export function get_history_count() {
    return request.get(historyCountUrl)
}

const bugCountUrl = "/bug_count"

export function get_bug_count() {
    return request.get(bugCountUrl)
}

const runUrl = "/run"

export function run(params) {
    return request.post(runUrl, {
        params
    })
}

const bugListUrl = "/bug_list"

export function get_bug_list() {
    return request.get(bugListUrl)
}

const downloadUrl = "/download/"

export function download(bug_id) {
    return request.get(downloadUrl + bug_id, {
        responseType: 'blob'
    })
}

const viewUrl = "/view/"

export function get_graph(bug_id) {
    return request.get(viewUrl + bug_id)
}