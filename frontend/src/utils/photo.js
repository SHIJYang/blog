/**
 * 记忆照片访问地址。
 * <p>
 * 受控存储的照片走 /api/memory/photo/{id}，<img> 无法携带 Authorization 头，
 * 因此把 JWT 与封存解锁令牌拼到查询参数里（后端两种方式都接受）。
 * 历史 picui 公网图原样返回。
 */
export function photoUrl(url) {
  if (!url) return ''
  if (!String(url).startsWith('/api/memory/photo/')) return url
  const token = localStorage.getItem('token')
  const sealedToken = localStorage.getItem('sealedToken')
  const params = []
  if (token) params.push(`token=${encodeURIComponent(token)}`)
  if (sealedToken) params.push(`sealedToken=${encodeURIComponent(sealedToken)}`)
  return params.length ? `${url}?${params.join('&')}` : url
}
