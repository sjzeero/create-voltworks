# CEE: Voltworks

Addon for Create: Electro Energetics (CEE in the name stands for it). It ports the microelectronics from Create: Power Grid (schematics, the circuit design table, board parts and the board solver) onto EE's electrical network, and adds blank, copper and golden circuit boards (idea taken from Create: New Age) that some of the EE machines need. More mechanics will be added over time.

Blank circuits come out of a superheated press, copper ones from a deployer, golden ones from sequenced assembly.
Motors, relays, transformers and a few others now need a copper circuit, the voltage regulator, energy meters and the SF6 breaker need golden ones.

Needs NeoForge 1.21.1, Create 6.0.x and Electro Energetics 1.1.x.

Build with `./gradlew build`, the jar (`create-voltworks-<mc version>-<version>.jar`) ends up in build/libs.

## Wall wires

Holding any EE wire spool, press V (CEE: Voltworks category in controls, can be rebound) to switch between AIR and WALL mode. The current mode is shown above the hotbar while a spool is in hand. AIR is EE's normal hanging wire.

In WALL mode the wire lies flat on block faces. Right-click a node to start, then right-click points on floors, walls or ceilings; the wire snaps to the 16x16 pixel grid of each face, runs in straight lines, turns corners and wraps around onto other faces and blocks. Right-click another node to finish. Sneak + right-click removes the last point (or cancels). The first and last points must be close to their nodes (`maxLead`).

The wire is drawn as a small box on the pixel grid: 1x1 pixel for normal wires, 3x3 pixels for heavily insulated ones, with the wire's own texture. The ends run straight into the connectors. The chosen mode is remembered in `voltworks-client.toml` (`wallMode`).

It is a normal EE connection: same wire type, resistance from the routed length, same current limit and burning, same drops. Breaking a block it lies on or one of its ends drops it, and an empty spool picks it up like an air wire. Routes are checked again on the server and saved with the world (`voltworks_wall_wires`). Limits are in the server config under `wallWires` (`maxLength`, `maxLead`, `maxPoints`); a route can never be longer than the wire type allows.

## Circuit board (work in progress, branch pg-boards-poc)

A circuit board block built from a schematic, using the schematic model, parts and solver ported from Create: Power Grid (see NOTICE). Each connector on the schematic becomes a pin of one Electro Energetics device; the parts run in their own small network that is coupled to EE at the pins every microtick.

Parts: resistor, capacitor, inductor, diode, varistor, npn and pnp transistors, vfet, triode, pentode, thyratron, barretter and regulator tubes, relays (spdt, dpdt, redstone), switch, button, potentiometer, fuse holder, voltage and current gauges, light and neon bulbs, display module, vias, connectors and labels. Parts heat up and burn like in Power Grid.

Schematics are drawn on the circuit design table, the same table and editor as in Power Grid. It needs 30 W from EE (about 150 V across its two nodes, it is a 480 ohm load) unless you are in creative. Put a blank schematic in, click the canvas to open the editor, pick parts from the inventory, route traces, and confirm. The table writes the schematic onto the item. Schematics can also be saved to and loaded from `circuits/*.nbt` in the game folder. A written schematic sneak-used in creative gives a board.

The schematic lives in the block's `Board.Schematic` data and is kept on the dropped item. Clients get the schematic only when it changes and small state updates (switch, relay and fuse states, bulb brightness) otherwise. In world, switches, buttons, relays and potentiometers can be clicked (sneak lowers a potentiometer, an iron nugget resets a blown fuse), bulbs light the block, gauges show readings on goggles and output redstone at the board edge. Sneak-click in creative repairs burnt parts.

### Board colours

Boards come in green, white, black, orange, red, blue, yellow and purple. Right-click a placed board with one of those dyes (not on a clickable part) to recolour it, or craft a board item with a dye. The colour is a block state (`color`), is kept on the dropped item and on pick-block, and does not change the schematic. Other dyes do nothing.

### Boards next to each other

Vias with edge connections join the matching via on the board beside them (same row, mirrored column), vias with vertical passthrough join the via straight above or below on a stacked board, like Power Grid's headers. Linked vias are one node, so current flows from board to board.

### Cooling, goggles and the table

An encased fan blowing through a board cools its parts; the effect scales with fan speed (`encasedFanCooling` in the server config). With goggles, looking at a part on a board shows that part's readings (gauges, bulbs and so on), sneaking adds the part's name and temperature and the hottest part on the board.

The design table heats up with the power it takes, like in Power Grid: it smokes and goggles turn the temperature red when it gets close, and past 175 °C it explodes. About 150 V keeps it warm but alive, 200 V blows it up within a second.

`HighContrastTraces` in `voltworks-client.toml` draws top traces red and bottom traces green in the editor; the colours and alphas can be changed in the same file.

### Crafting

The crafting chain follows Power Grid:

1. Draw a schematic on the design table (Create empty schematic in, parts from the inventory). A deployer holding a written schematic copies it onto an empty schematic.
2. Sequenced assembly on the written schematic, two loops: deploy an empty circuit (wooden slab on a saw), dough, a copper sheet, then press. Gives an unetched circuit that keeps the schematic.
3. Acid: redstone, blaze powder and 250 mB water in a heated basin. Etching: the unetched circuit with 250 mB acid in a heated basin gives an incomplete circuit.
4. Insert the parts listed on the schematic with a mechanical arm (as in Power Grid), a deployer, or by right-clicking a part onto the circuit in the inventory. When the last part is in, the circuit turns into the board.

NPN and PNP transistors come from sequenced assembly like in Power Grid, the other parts and the table are crafted. Power Grid ingredients that do not exist here are replaced:

| Power Grid | Voltworks |
|---|---|
| conductive casing | andesite casing |
| copper plating | copper block |
| socket | EE connector |
| pins | copper nuggets |
| resistive coil | EE iron wire |
| copper coil | inductor (Power Grid's inductor part is its copper coil) |
| zinc sheet | zinc ingot |
| heavy wire connector | EE insulator |
| gold wire | EE electrum wire |
| wire connector, LV switch/button/light bulb, meters | the matching board parts |

JEI shows the circuit design and circuit assembly categories.

Test commands (near spawn, creative batteries wired to the board):

- `/voltworks boardtest setup <relay|vfet|triode|fuse|overheat|gauge|controls>`
- `/voltworks boardtest schematic <preset>` gives a written schematic of a preset
- `/voltworks boardtest wire`, `battery <0|1> <volts>`
- `/voltworks boardtest use <part> [sneak|item]`, `repair`
- `/voltworks boardtest read|world`, `readat <pos>`
- `/voltworks boardtest links <edge|header>` builds two linked boards with a supply and a resistor
- `/voltworks boardtest many <count> <preset>`, `clearmany`, `perf reset|report` for load tests (100 boards cost about 0.5 ms per tick on the server thread and 0.3 to 0.4 ms on the sim thread)
- `/voltworks boardtest attach <device pos> <battery pos> <volts>` puts a creative battery at the second position and wires it to any EE device, e.g. a board or the design table
- `/voltworks wallwiretest <pos>` builds a small room with two battery and bulb circuits on wall wires (copper over a wall, heavily insulated along a ceiling)

`./gradlew test` builds boards from schematics and checks them against values from Power Grid's own solver.
