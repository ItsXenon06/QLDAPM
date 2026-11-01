"""Expose only the signed SePay webhook, never the application or login page."""
import argparse
import http.client
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

parser = argparse.ArgumentParser()
parser.add_argument('--port', type=int, default=18082)
parser.add_argument('--app-port', type=int, default=8081)
args = parser.parse_args()
path = '/api/payments/sepay/webhook'

class Handler(BaseHTTPRequestHandler):
    def setup(self):
        super().setup()
        self.connection.settimeout(15)

    def log_message(self, *unused):
        pass  # Do not log webhook payloads, signatures or account information.

    def reject(self, status):
        self.send_response(status)
        self.send_header('Content-Length', '0')
        self.end_headers()

    def do_GET(self):
        self.reject(404)

    def do_POST(self):
        if self.path != path:
            return self.reject(404)
        try:
            size = int(self.headers.get('Content-Length', '0'))
            if not 0 < size <= 32768 or self.headers.get('Transfer-Encoding'):
                return self.reject(413)
            if self.headers.get('Content-Type', '').split(';')[0].lower() != 'application/json':
                return self.reject(415)
            signature = self.headers.get('X-SePay-Signature')
            timestamp = self.headers.get('X-SePay-Timestamp')
            if not signature or not timestamp:
                return self.reject(401)
            body = self.rfile.read(size)
            if len(body) != size:
                return self.reject(400)
            upstream = http.client.HTTPConnection('127.0.0.1', args.app_port, timeout=25)
            try:
                upstream.request('POST', path, body, {
                    'Content-Type': 'application/json',
                    'X-SePay-Signature': signature,
                    'X-SePay-Timestamp': timestamp,
                })
                result = upstream.getresponse()
                data = result.read(32768)
                self.send_response(result.status)
                self.send_header('Content-Type', 'application/json')
                self.send_header('Content-Length', str(len(data)))
                self.end_headers()
                self.wfile.write(data)
            finally:
                upstream.close()
        except (OSError, ValueError, http.client.HTTPException):
            self.reject(502)

ThreadingHTTPServer(('127.0.0.1', args.port), Handler).serve_forever()
