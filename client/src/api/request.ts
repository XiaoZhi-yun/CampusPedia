import axios from 'axios'

const request = axios.create({ baseURL: '/api', timeout: 120000 })

request.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = 'Bearer ' + token
  return config
})

request.interceptors.response.use(
  (resp) => resp.data,
  (err) => Promise.reject(err)
)

export default request
