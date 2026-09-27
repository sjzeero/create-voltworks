# EE Circuits

Small addon for Create: Electro Energetics. Adds blank, copper and golden circuit boards (idea taken from Create: New Age) and makes some of the EE machines need them.

Blank circuits come out of a superheated press, copper ones from a deployer, golden ones from sequenced assembly.
Motors, relays, transformers and a few others now need a copper circuit, the voltage regulator, energy meters and the SF6 breaker need golden ones.

Needs NeoForge 1.21.1, Create 6.0.x and Electro Energetics 1.1.x.

Build with `./gradlew build`, the jar ends up in build/libs.

## Circuit board (work in progress, branch pg-boards-poc)

A circuit board block built from a schematic, using the schematic model, parts and solver ported from Create: Power Grid (see NOTICE). Each connector on the schematic becomes a pin of one Electro Energetics device; the parts run in their own small network that is coupled to EE at the pins every microtick.

Parts: resistor, capacitor, inductor, diode, varistor, npn and pnp transistors, vfet, triode, pentode, thyratron, barretter and regulator tubes, relays (spdt, dpdt, redstone), switch, button, potentiometer, fuse holder, voltage and current gauges, light and neon bulbs, display module, vias, connectors and labels. Parts heat up and burn like in Power Grid.

Schematics are drawn on the circuit design table, the same table and editor as in Power Grid. It needs 30 W from EE (about 150 V across its two nodes, it is a 480 ohm load) unless you are in creative. Put a blank schematic in, click the canvas to open the editor, pick parts from the inventory, route traces, and confirm. The table writes the schematic onto the item. Schematics can also be saved to and loaded from `circuits/*.nbt` in the game folder. There are no recipes for the table, schematics, parts or boards yet, they come from the creative tab, and a written schematic sneak-used in creative gives a board.

The schematic lives in the block's `Board.Schematic` data and is kept on the dropped item. Clients get the schematic only when it changes and small state updates (switch, relay and fuse states, bulb brightness) otherwise. In world, switches, buttons, relays and potentiometers can be clicked (sneak lowers a potentiometer, an iron nugget resets a blown fuse), bulbs light the block, gauges show readings on goggles and output redstone at the board edge. Sneak-click in creative repairs burnt parts.

Test commands (near spawn, creative batteries wired to the board):

- `/eecircuits boardtest setup <relay|vfet|triode|fuse|overheat|gauge|controls>`
- `/eecircuits boardtest wire`, `battery <0|1> <volts>`
- `/eecircuits boardtest use <part> [sneak|item]`, `repair`
- `/eecircuits boardtest read|world`
- `/eecircuits boardtest attach <device pos> <battery pos> <volts>` puts a creative battery at the second position and wires it to any EE device, e.g. a board or the design table

`./gradlew test` builds boards from schematics and checks them against values from Power Grid's own solver.
