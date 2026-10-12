"""The same tiny werkzeug app from the Python track — reused here so the Guardener
module migrates a Dockerfile you already understand.

Nothing in this file matters to Guardener; it only reads the *Dockerfile* and the
build context. The app is here so the migrated image actually builds and serves.
"""
from importlib.metadata import version

from werkzeug.serving import run_simple
from werkzeug.wrappers import Request, Response


@Request.application
def app(request):
    return Response(
        "Chainguard Guardener demo\n"
        f"This response is served by werkzeug {version('werkzeug')}\n",
        mimetype="text/plain",
    )


if __name__ == "__main__":
    # Bind on all interfaces so the container port can be published.
    run_simple("0.0.0.0", 8080, app)
