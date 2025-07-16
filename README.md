# Anthropic Agent Tool Framework

A Kotlin-based framework for creating and managing tools that can be used with Anthropic's Claude AI model. This project provides a structured way to implement tool-based interactions with Claude, allowing for file operations and other system interactions.

## Features

- Tool-based interaction system with Anthropic's Claude AI
- Built-in file operation tools:
    - File reading
    - File listing
    - File modification
    - File creation
    - File deletion
- Extensible tool framework
- Interactive command-line interface

## Prerequisites

- Kotlin
- Anthropic API key (set as an `ANTHROPIC_API_KEY` environment variable)

## Setup

1. Clone the repository
2. Set your Anthropic API key as an environment variable
3. Build the project using Gradle:
```bash
./gradlew build
```

## Project Structure

- `src/main/kotlin/`
    - `anthropic/` - Anthropic agent implementation
    - `tool/` - Tool framework and implementations
        - `files/` - File operation tools
    - `Main.kt` - Application entry point

## Usage

Run the application:

```bash
./gradlew run
```

The application will start an interactive session where you can communicate with Claude AI. Type 'exit' to quit the session.

## Extending with New Tools

1. Create a new tool by implementing the `ToolSetup` interface:

```kotlin
interface ToolSetup<Input> {
    val name: String
    val description: String
    operator fun invoke(input: Input): String
}
```

2. Define the input parameters using a data class with `@InputParamDescription` annotations
3. Add the tool to the tools list in `Main.kt`

## Dependencies

- Anthropic Java Client: `com.anthropic:anthropic-java:1.0.0`
- Kotlin Coroutines: For asynchronous operations
- Jackson: For JSON processing
- Kotlin Reflect: For runtime reflection

## License

[License information not provided in source]

Copyright © 2025 Artur Dumchev

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.