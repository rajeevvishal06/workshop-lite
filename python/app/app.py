"""A tiny web app whose server IS the hero library.

Werkzeug 3.0.2 carries CVE-2024-34069 (the Werkzeug debugger RCE). This app is
served by Werkzeug itself, so the vulnerable dependency is the very thing handling
each request. Nothing in this file changes across the three acts — only *where the
werkzeug package is installed from* changes.
"""
from importlib.metadata import version

from werkzeug.serving import run_simple
from werkzeug.wrappers import Request, Response


@Request.application
def app(request):
    return Response(
        "Chainguard Libraries demo\n"
        f"This response is served by werkzeug {version('werkzeug')}\n",
        mimetype="text/plain",
    )


if __name__ == "__main__":
    # Bind on all interfaces so the container port can be published.
    run_simple("0.0.0.0", 8080, app)
