[![Build Status](https://ci.zachduda.com/job/AnimatedInventory/badge/icon)](https://ci.zachduda.com/job/AnimatedInventory/)


# AnimatedInventory
AnimatedInventory helps to spice up your server with several animations to clear you inventory with. Not to mention it's unique hotbar fortunes and one-of-a-kind /ai undoclear which allows users to actually reverse time and restore accidental inventory clears!

# Spigot
Use AnimatedInventory for yourself at: https://zachduda.com/animatedinventory

# Custom Animations
Every clear animation has its own file in `plugins/AnimatedInventory/Animations/`. The plugin starts you off with `panes.yml`, `rainbow.yml`, `explosion.yml`, `water.yml` and `fireball.yml`. Each file sets the items drawn on the hotbar, every frame and how long to wait before it, and the sounds, particles and action bar text along the way.

To make your own, copy one of them to a new name, change it, then run `/ai reload`. `/ai clear <player> <animation>` plays a specific one, so you can try it out. `README.txt` in the same folder explains every option.

# Getting Started with the API

## Maven
AnimatedInventory is available for developers via Maven:
```xml
<repository>
    <id>zachduda</id>
    <url>https://zachduda.com/maven</url>
</repository>
```
```xml
<dependency>
  <groupId>com.github.zachduda</groupId>
  <artifactId>animatedinventory</artifactId>
  <version>7.8</version>
</dependency>
```

AnimatedInventory is equipped with an API for developers to use. See our Spigot Wiki page below for help on how to use it!
https://www.spigotmc.org/wiki/animatedinventoryapi/

# Pull Requests and A.I Ethics
Pull Requests that contain write-ups or code written partially or fully by Artificial Intelligence (A.I) and not labeled as such will be immediately rejected. Write-ups should be short, concise, and human written, and contributed code should be thoroughly audited by human eyes. For more information about this stance, visit https://zachduda.com/blog/ai-pull-requests

# License
This project is licensed under [Creative Commons (CC-BY-NC-4)](https://creativecommons.org/licenses/by-nc/4.0/).
You can do whatever you'd like: just give credit and make sure it's non-commerical.
