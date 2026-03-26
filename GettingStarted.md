# Getting Started

Follow these instructions to set up your development environment and run the project.

## Prerequisites

Ensure you have the following installed:

1.  **Java 21**: The project requires Java 21.
2.  **Maven**: Used for dependency management and building the project.
3.  **Make**: Used to run convenient build and execution commands.

### Installing Make on Windows
If you are using Windows, the easiest way to install Make is via [Chocolatey](https://chocolatey.org/):

```powershell
choco install make
```

## Setup and Installation

Once the prerequisites are installed, initialize the project by running:

```bash
make install
```

This command cleans the project and builds all modules while skipping tests to speed up the process.

## Running the Application

After a successful installation, you can run the server and client using the following commands:

### Run Server
```bash
make run-server
```

### Run Client
```bash
make run-client
```
