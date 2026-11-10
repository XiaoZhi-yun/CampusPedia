import { defineStore } from 'pinia'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    studentNo: localStorage.getItem('studentNo') || '',
    realName: localStorage.getItem('realName') || '',
    major: localStorage.getItem('major') || '',
    className: localStorage.getItem('className') || '',
    avatar: localStorage.getItem('avatar') || 'p1',
    role: localStorage.getItem('role') || '',
  }),
  actions: {
    setLogin(data: any) {
      this.token = data.token
      this.studentNo = data.studentNo || ''
      this.realName = data.realName || ''
      this.major = data.major || ''
      this.className = data.className || ''
      this.avatar = data.avatar || 'p1'
      this.role = data.role
      localStorage.setItem('token', data.token)
      localStorage.setItem('studentNo', data.studentNo || '')
      localStorage.setItem('realName', data.realName || '')
      localStorage.setItem('major', data.major || '')
      localStorage.setItem('className', data.className || '')
      localStorage.setItem('avatar', data.avatar || 'p1')
      localStorage.setItem('role', data.role)
    },
    logout() {
      this.token = ''; this.studentNo = ''; this.realName = ''; this.major = ''; this.className = ''
      this.avatar = 'p1'; this.role = ''
      localStorage.clear()
    },
  },
})
