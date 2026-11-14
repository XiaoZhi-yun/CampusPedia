import { defineStore } from 'pinia'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: sessionStorage.getItem('token') || '',
    studentNo: sessionStorage.getItem('studentNo') || '',
    username: sessionStorage.getItem('username') || '',
    major: sessionStorage.getItem('major') || '',
    className: sessionStorage.getItem('className') || '',
    avatar: sessionStorage.getItem('avatar') || 'p1',
    role: sessionStorage.getItem('role') || '',
  }),
  actions: {
    setLogin(data: any) {
      this.token = data.token
      this.studentNo = data.studentNo || ''
      this.username = data.username || ''
      this.major = data.major || ''
      this.className = data.className || ''
      this.avatar = data.avatar || 'p1'
      this.role = data.role
      sessionStorage.setItem('token', data.token)
      sessionStorage.setItem('studentNo', data.studentNo || '')
      sessionStorage.setItem('username', data.username || '')
      sessionStorage.setItem('major', data.major || '')
      sessionStorage.setItem('className', data.className || '')
      sessionStorage.setItem('avatar', data.avatar || 'p1')
      sessionStorage.setItem('role', data.role)
    },
    logout() {
      this.token = ''; this.studentNo = ''; this.username = ''; this.major = ''; this.className = ''
      this.avatar = 'p1'; this.role = ''
      sessionStorage.clear()
    },
  },
})
