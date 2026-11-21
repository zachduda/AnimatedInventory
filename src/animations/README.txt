==============================================================================
 AnimatedInventory - Clear Animations
==============================================================================

Every .yml file in this folder is an animation that can play when an
inventory is cleared. Each clear picks one of the enabled ones at random.

 - To change an animation, edit its file, then run /ai reload.
 - To turn one off, set "enabled: false" in it. The animations that come
   with the plugin are put back if their file is deleted.
 - To make your own, copy one of the files to a new name, change it, then
   run /ai reload. File names can use letters, numbers, - and _, and have
   to end in .yml (not .yaml).
 - To try one out: /ai clear <player> <animation>

If a file has a mistake in it, the console says what and where, and that
animation is skipped until it's fixed.

This file is replaced each time the plugin loads, so keep your own notes
somewhere else.

Times are in ticks: 20 ticks = 1 second.

------------------------------------------------------------------------------
 An animation file
------------------------------------------------------------------------------

  enabled: true      Whether this animation can play. Default: true
  weight: 1          How often it's picked compared to the others. One with
                     weight 2 plays twice as often as one with weight 1.
                     Default: 1
  items:             What the frames draw with.
  frames:            Played from top to bottom.
  finish:            What happens after the last frame.

An animation can be at most 1200 ticks (1 minute) long.

------------------------------------------------------------------------------
 items
------------------------------------------------------------------------------

Each item gets a symbol, one letter or digit, that frames use to put it on
the hotbar. Symbols are case sensitive, so W and w can be different items.

  items:
    W: WHITE_STAINED_GLASS_PANE

To set more than the material:

  items:
    W:
      material: WHITE_STAINED_GLASS_PANE
      name: '&fWhoosh!'        Shown when the slot is selected.
                               Default: no name at all (so is name: '')
      lore: ['&7First line', '&7Second line']
                               Or a block of text, one line per line:
                                 lore: |
                                   &7First line
                                   &7Second line
      amount: 3                The number shown on the item, 1 to 64.
                               Default: 1

Material names:
https://hub.spigotmc.org/javadocs/spigot/org/bukkit/Material.html

------------------------------------------------------------------------------
 frames
------------------------------------------------------------------------------

  frames:
    - delay: 4             Ticks to wait after the frame before it. For the
                           first frame, ticks after the clear starts.
                           Default: 0
      hotbar: 'gM.......'  The 9 hotbar slots, from left to right:
                             a symbol   puts that item in the slot
                             .          leaves the slot as it is
                             _          empties the slot
                           Use exactly 9 symbols, and keep the quotes.
      hold: 1              Selects a hotbar slot, 0 to 8 from the left.
                           Does nothing when slot-switching is off in
                           config.yml.
      sounds: [...]        Sounds to play. See "sounds" below.
      stop-sounds: [...]   Sounds to cut off, like a long one that an
                           earlier frame started.
      particles: [...]     Particles to show. See "particles" below.
      action-bar: '%progress-msg%'
                           Text to show above the hotbar.

Any of these can be left out, so a frame with only a delay is a pause.

A . keeps whatever is already in the slot. Until a frame draws over it,
that's the player's own item, so drawing a few slots at a time makes the
animation look like it's eating through the hotbar.

------------------------------------------------------------------------------
 finish
------------------------------------------------------------------------------

The finish clears the inventory, then shows done-msg from config.yml.

  finish:
    delay: 4               Ticks to wait after the last frame. Default: 0
    sounds: [...]
    stop-sounds: [...]
    particles: [...]
    action-bar: '&a&lPoof!'    Shown instead of done-msg.

------------------------------------------------------------------------------
 sounds
------------------------------------------------------------------------------

  sounds:
    - ENTITY_CHICKEN_EGG                                  At volume 1, pitch 1.
    - {sound: ENTITY_CHICKEN_EGG, volume: 2.0, pitch: 0.5}

  volume   0 to 10. Default: 1
  pitch    0 to 2, where 1 is normal. Default: 1

Only the player hears them. Names work with dots or underscores, so
entity.chicken.egg is the same as ENTITY_CHICKEN_EGG. A sound from a
resource pack plays with its full name, like myserver:clear.whoosh

stop-sounds only needs the names:

  stop-sounds: [ENTITY_MINECART_INSIDE]

Sound names:
https://hub.spigotmc.org/javadocs/spigot/org/bukkit/Sound.html

------------------------------------------------------------------------------
 particles
------------------------------------------------------------------------------

  particles:
    - FLAME                                               Just one particle.
    - {particle: FLAME, count: 3, offset: [0.4, 0.2, 0.4], speed: 0.1}

  count    How many, 0 to 1000. Default: 1
  offset   How far they spread out from the middle, as [x, y, z], or one
           number for all three. Default: 0
  speed    How fast they move. Default: 0
  height   Blocks above the player's feet. Default: 1

Everyone nearby can see them. Some particles need more options:

  DUST                         color: '#FF0000'    and if you like, size: 2
  DUST_COLOR_TRANSITION        color: '#FF0000', to-color: '#0000FF'
                               and if you like, size: 2
  ENTITY_EFFECT, FLASH, ...    color: '#FF0000'
  EFFECT, INSTANT_EFFECT       Nothing, but if you like:
                                 color: '#FF0000'    Default: white
                                 power: 2            0 to 10. Default: 1
  TRAIL                        color: '#FF0000', target: [0, 2, 0]
                               and if you like, duration: 40
                                 target    Where the trail ends, in blocks
                                           from where it starts, each
                                           from -16 to 16.
                                 duration  Ticks it takes, 1 to 1200.
                                           Default: 20
  BLOCK, FALLING_DUST, ...     block: STONE
  ITEM                         item: DIAMOND

A few particles, like VIBRATION and SHRIEK, need settings these files can't
set. The console says so, and suggests one that looks alike.

Some of these particles, and their options, only exist on newer servers. On
an older one, the console says which option was ignored or what to change.

An option the particle doesn't use, like a color on BLOCK, is ignored, and
the console points it out.

A color can also be written as [255, 0, 0]. Keep the quotes around
'#FF0000', since without them YAML ignores everything after the #.

Particles don't show when use-particles is false in config.yml.

Particle names (the names from before 1.20.5 work too):
https://hub.spigotmc.org/javadocs/spigot/org/bukkit/Particle.html

------------------------------------------------------------------------------
 Text
------------------------------------------------------------------------------

Item names, lore and action-bar can use & color codes. action-bar can also
use these:

  %player%          The player's name.
  %progress-msg%    progress-msg from config.yml.
  %done-msg%        done-msg from config.yml.
