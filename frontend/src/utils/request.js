import axios from "axios";


const request  = axios.create({
    baseURL: 'http://localhost:8000/',
    timeout: 300000,
})

export default request;
