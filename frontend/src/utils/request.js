import axios from "axios";


const request  = axios.create({
    baseURL: import.meta.env.VITE_BACKEND_URL,
    timeout: 300000,
})

export default request;
