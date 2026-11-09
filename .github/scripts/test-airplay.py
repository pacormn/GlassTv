import socket
# Exercise the actual native RTSP listener packaged in the Android APK.
with socket.create_connection(("127.0.0.1", 17000), timeout=10) as client:
    client.settimeout(10)
    client.sendall(b"OPTIONS * RTSP/1.0\r\nCSeq: 1\r\nUser-Agent: GlassTV-CI\r\n\r\n")
    response = client.recv(8192)
    assert b"200 OK" in response, response
    assert b"Public:" in response, response
    print("AirPlay native RTSP OPTIONS passed")
