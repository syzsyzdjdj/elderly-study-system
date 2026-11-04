import request from '../utils/request'

export function getWeather() {
  return request.get('/weather/current', { silent: true, timeout: 30000 })
}
