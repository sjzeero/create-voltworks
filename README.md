# EE Circuits

Small addon for Create: Electro Energetics. Adds blank, copper and golden circuit boards (idea taken from Create: New Age) and makes some of the EE machines need them.

Blank circuits come out of a superheated press, copper ones from a deployer, golden ones from sequenced assembly.
Motors, relays, transformers and a few others now need a copper circuit, the voltage regulator, energy meters and the SF6 breaker need golden ones.

Needs NeoForge 1.21.1, Create 6.0.x and Electro Energetics 1.1.x.

Build with `./gradlew build`, the jar ends up in build/libs.

## Circuit board (work in progress, branch pg-boards-poc)

A circuit board block with 4 pins that acts as a single Electro Energetics device. The parts on a board run in their own small network using the Java solver ported from Create: Power Grid (see NOTICE), and every microtick the board is coupled to EE at its pins through an equivalent conductance and current source per pin.

Parts: resistor, capacitor, diode, npn and pnp transistor. For now a board's parts are a netlist stored in the block's NBT (`Parts`), there is no GUI yet.

Test commands (near spawn, creative batteries wired to the board):

- `/eecircuits boardtest setup <resistor|diode|halfwave|rc|npn|pnp|stiffdiode|stiffres>`
- `/eecircuits boardtest load <preset>` resets the parts on an existing board
- `/eecircuits boardtest battery <0|1> <volts>`, `ac <0|1>`
- `/eecircuits boardtest read|wave|trace <pad> <n>|stats <n>`

`./gradlew test` runs the solver checks against values from Power Grid's own solver.
