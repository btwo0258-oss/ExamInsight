import urllib.request, json
req = urllib.request.Request('http://localhost:8080/api/user/forgot-password', data=b'{\"username\":\"20260325\"}', headers={'Content-Type': 'application/json'})
res = urllib.request.urlopen(req)
print(res.read().decode())