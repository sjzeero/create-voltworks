# EE Circuits

Small addon for Create: Electro Energetics. Adds blank, copper and golden circuit boards (idea taken from Create: New Age) and makes some of the EE machines need them.

Blank circuits come out of a superheated press, copper ones from a deployer, golden ones from sequenced assembly.
Motors, relays, transformers and a few others now need a copper circuit, the voltage regulator, energy meters and the SF6 breaker need golden ones.

Needs NeoForge 1.21.1, Create 6.0.x and Electro Energetics 1.1.x.

Build with `./gradlew build`, the jar ends up in build/libs.

## Circuit board (work in progress, branch pg-boards-poc)

A circuit board block that is a single Electro Energetics device. Its pins are EE nodes and the parts on the board are baked into EE's network each tick. Right now only a resistor part exists, ported from Create: Power Grid (see NOTICE).

`/eecircuits boardtest place|wire|series|read` sets up a creative battery wired to a board near spawn and prints the board voltage and current.
