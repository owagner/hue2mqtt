language: java
before_deploy: "gradle jar"
deploy:
  provider: releases
  api_key:
    secure: BSI9PlQhhOMXUm2cr8YZs+yT5vX+dLyuPNpx8uvbO1R4TvnT2hZlVt4CDV3iQUNxMF5dD+1NIbZGKfXYk7bH2Ei8AGpiObhl7peFjX9wezqV5aRct2A3eS50mO7uzD3DOt7+6jcpVzTzwiNuzRkWRuSsOocTQc5kE4A5OfcAspY=
  file: "build/libs/hue2mqtt.jar"
  skip_cleanup: true
  on:
    repo: owagner/hue2mqtt
    tags: true
    all_branches: true
