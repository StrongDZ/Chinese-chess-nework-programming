# Chinese Chess Frontend

JavaFX frontend application for Chinese Chess.

## Requirements

- Java 17 or higher
- Maven 3.6+

## Building and Running

### Using Maven:

```bash
# Compile the project
mvn clean compile

# Run the application
mvn javafx:run
```

### Alternative: Manual compilation (requires JavaFX SDK)

If you have JavaFX SDK installed:

```bash
# Set JAVA_HOME to your JDK
export JAVA_HOME=/path/to/jdk

# Set JavaFX path (adjust to your JavaFX installation)
export PATH_TO_FX=/path/to/javafx-sdk/lib

# Compile
javac --module-path $PATH_TO_FX --add-modules javafx.controls,javafx.graphics \
    -d build src/module-info.java src/application/**/*.java

# Run
java --module-path $PATH_TO_FX --add-modules javafx.controls,javafx.graphics \
    -cp build application.Main
```

## Project Structure

- `src/application/` - Main application code
- `src/application/components/` - UI components
- `src/application/state/` - Application state management
- `src/application/util/` - Utility classes
- `assets/` - Image assets

