# EE Circuits

Small addon for Create: Electro Energetics. Adds blank, copper and golden circuit boards (idea taken from Create: New Age) and makes some of the EE machines need them.

Blank circuits come out of a superheated press, copper ones from a deployer, golden ones from sequenced assembly.
Motors, relays, transformers and a few others now need a copper circuit, the voltage regulator, energy meters and the SF6 breaker need golden ones.

Needs NeoForge 1.21.1, Create 6.0.x and Electro Energetics 1.1.x.

Build with `./gradlew build`, the jar ends up in build/libs.

## Circuit board (work in progress, branch pg-boards-poc)

A circuit board block built from a schematic, using the schematic model, parts and solver ported from Create: Power Grid (see NOTICE). Each connector on the schematic becomes a pin of one Electro Energetics device; the parts run in their own small network that is coupled to EE at the pins every microtick.

Parts: resistor, capacitor, inductor, diode, varistor, npn and pnp transistors, vfet, triode, pentode, thyratron, barretter and regulator tubes, relays (spdt, dpdt, redstone), switch, button, potentiometer, fuse holder, voltage and current gauges, light and neon bulbs, display module, vias, connectors and labels. Parts heat up and burn like in Power Grid.

Schematics are drawn on the circuit design table, the same table and editor as in Power Grid. It needs 30 W from EE (about 150 V across its two nodes, it is a 480 ohm load) unless you are in creative. Put a blank schematic in, click the canvas to open the editor, pick parts from the inventory, route traces, and confirm. The table writes the schematic onto the item. Schematics can also be saved to and loaded from `circuits/*.nbt` in the game folder. A written schematic sneak-used in creative gives a board.

The schematic lives in the block's `Board.Schematic` data and is kept on the dropped item. Clients get the schematic only when it changes and small state updates (switch, relay and fuse states, bulb brightness) otherwise. In world, switches, buttons, relays and potentiometers can be clicked (sneak lowers a potentiometer, an iron nugget resets a blown fuse), bulbs light the block, gauges show readings on goggles and output redstone at the board edge. Sneak-click in creative repairs burnt parts.

### Crafting

The crafting chain follows Power Grid:

1. Draw a schematic on the design table (Create empty schematic in, parts from the inventory). A deployer holding a written schematic copies it onto an empty schematic.
2. Sequenced assembly on the written schematic, two loops: deploy an empty circuit (wooden slab on a saw), dough, a copper sheet, then press. Gives an unetched circuit that keeps the schematic.
3. Acid: redstone, blaze powder and 250 mB water in a heated basin. Etching: the unetched circuit with 250 mB acid in a heated basin gives an incomplete circuit.
4. Insert the parts listed on the schematic with a mechanical arm (as in Power Grid), a deployer, or by right-clicking a part onto the circuit in the inventory. When the last part is in, the circuit turns into the board.

NPN and PNP transistors come from sequenced assembly like in Power Grid, the other parts and the table are crafted. Power Grid ingredients that do not exist here are replaced:

| Power Grid | EE Circuits |
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

- `/eecircuits boardtest setup <relay|vfet|triode|fuse|overheat|gauge|controls>`
- `/eecircuits boardtest schematic <preset>` gives a written schematic of a preset
- `/eecircuits boardtest wire`, `battery <0|1> <volts>`
- `/eecircuits boardtest use <part> [sneak|item]`, `repair`
- `/eecircuits boardtest read|world`
- `/eecircuits boardtest attach <device pos> <battery pos> <volts>` puts a creative battery at the second position and wires it to any EE device, e.g. a board or the design table

`./gradlew test` builds boards from schematics and checks them against values from Power Grid's own solver.
