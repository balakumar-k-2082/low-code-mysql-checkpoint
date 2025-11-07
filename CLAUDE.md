# Using Claude Code with MySQL Checkpoint System

This guide explains how to use **Claude Code** terminal assistant to work with this project.

---

## What is Claude Code?

Claude Code is an AI-powered coding assistant that runs in your terminal. It can help you:
- ✅ Understand the codebase
- ✅ Fix bugs and errors
- ✅ Add new features
- ✅ Refactor code
- ✅ Write tests
- ✅ Debug issues

---

## Quick Start

### 1. Open Terminal in Project Directory

```bash
cd /home/user/low-code-mysql-checkpoint
```

### 2. Start Claude Code

```bash
# If Claude Code is installed globally:
claude

# Or if using specific version:
npx @anthropic-ai/claude-code
```

### 3. Ask Questions

Once Claude Code is running, you can ask questions naturally:

```
You: Explain how the TransactionCapture class works

Claude: The TransactionCapture class is responsible for...
```

---

## Common Commands and Use Cases

### Understanding the Code

**Ask about architecture:**
```
You: What's the overall architecture of this checkpoint system?
You: Explain the transaction-based approach
You: How does binlog capturing work?
```

**Explore specific classes:**
```
You: Show me the CheckpointManager class
You: How does UndoRedoManager generate reverse SQL?
You: Explain the TableMapper implementation
```

**Understand data flow:**
```
You: Walk me through what happens when a transaction commits
You: How does undo operation work step by step?
You: Trace the flow from binlog event to checkpoint creation
```

### Making Changes

**Add new features:**
```
You: Add a method to list all checkpoints for a user
You: Implement a revertTo(checkpointName) method
You: Add compression for large JSON events
```

**Fix bugs:**
```
You: The undo operation is failing with SQLException, help me debug
You: Why am I getting "table not found" error?
You: Fix the issue where events are captured out of order
```

**Refactor code:**
```
You: Refactor the SQL generation logic to be more maintainable
You: Extract the event filtering logic into a separate class
You: Improve error handling in CheckpointManager
```

### Debugging

**Analyze errors:**
```
You: I'm getting "REPLICATION SLAVE privilege" error, what should I do?
You: MySQL binlog format is STATEMENT instead of ROW, how do I fix this?
You: The CLI crashes with NullPointerException, help debug
```

**Understand logs:**
```
You: Explain these error logs: [paste logs]
You: What does this binlog event mean: [paste event]
```

### Testing

**Write tests:**
```
You: Write unit tests for TableMapper class
You: Create integration test for undo/redo operations
You: Add test cases for edge cases in transaction detection
```

**Run tests:**
```
You: How do I run the tests?
You: Create a test that verifies checkpoint isolation between users
```

### Documentation

**Generate docs:**
```
You: Generate JavaDoc for CheckpointManager
You: Create API documentation for all public methods
You: Write a tutorial for integrating this into Spring Boot app
```

---

## Project-Specific Commands

### Setup and Configuration

```
You: Help me configure MySQL for checkpoint system
You: Where do I put the server-id in my.cnf?
You: Create a SQL script to set up permissions
You: Generate a docker-compose.yml for development environment
```

### Building and Running

```
You: How do I build the project?
You: Run the CLI demo for me
You: Create a shell script to automate the build and run process
You: Package this as a Docker image
```

### Troubleshooting

```
You: The binlog is not capturing events, what could be wrong?
You: Why are checkpoints not being created automatically?
You: Events are missing from checkpoint_transaction_events table
You: MySQL is rejecting GTID events
```

### Extending Functionality

```
You: Add support for multiple users with conflict detection
You: Implement checkpoint naming with meaningful descriptions
You: Add a REST API to expose checkpoint operations
You: Create a web UI for managing checkpoints
```

---

## Best Practices

### 1. Be Specific

❌ **Bad:**
```
You: Fix the bug
```

✅ **Good:**
```
You: Fix the bug in UndoRedoManager.executeUndoTransaction() where
     DELETE operations are failing with FK constraint violations
```

### 2. Provide Context

❌ **Bad:**
```
You: Why doesn't this work?
```

✅ **Good:**
```
You: I'm trying to create a checkpoint but getting this error:
     [paste error]
     Here's my configuration:
     [paste config]
     What am I missing?
```

### 3. Ask for Examples

```
You: Show me an example of how to use CheckpointManager in a Spring Boot application
You: Give me code examples for handling undo with conflict detection
You: Provide a complete working example of integrating this with a REST API
```

### 4. Iterate

```
You: Create a method to get checkpoint history
Claude: [provides implementation]

You: Add pagination to that method
Claude: [adds pagination]

You: Now add filtering by date range
Claude: [adds filtering]
```

---

## Code Navigation

### Finding Code

```
You: Where is the transaction boundary detection implemented?
You: Find all classes that handle binlog events
You: Show me where checkpoints are stored in the database
You: Which file contains the undo logic?
```

### Understanding Relationships

```
You: How do CheckpointManager and TransactionCapture interact?
You: What classes depend on TableMapper?
You: Show me the data flow from binlog to checkpoint creation
```

### Reviewing Changes

```
You: Review the last commit
You: Explain the changes made in commit abc1234
You: What files were modified in the last update?
```

---

## Example Workflows

### Workflow 1: Adding a New Feature

```
You: I want to add a feature to name checkpoints meaningfully
     instead of "auto_123456". How should I approach this?

Claude: [Provides approach]

You: Implement the checkpoint naming feature

Claude: [Implements feature]

You: Add tests for the new feature

Claude: [Adds tests]

You: Update the README to document this feature

Claude: [Updates README]
```

### Workflow 2: Debugging an Issue

```
You: The undo operation is not working. Here's the error:
     [paste error]

Claude: [Analyzes error and suggests fixes]

You: Apply the first fix

Claude: [Applies fix]

You: Test if it works now

Claude: [Runs test]

You: It still fails with a different error: [paste error]

Claude: [Provides additional fix]
```

### Workflow 3: Understanding the Codebase

```
You: I'm new to this project. Give me an overview

Claude: [Provides overview]

You: Explain the transaction-based approach in detail

Claude: [Detailed explanation]

You: Show me the key classes I should understand first

Claude: [Lists and explains key classes]

You: Walk me through a complete undo/redo cycle

Claude: [Step-by-step walkthrough]
```

---

## Tips and Tricks

### 1. Use Git Integration

```
You: What changed since the last commit?
You: Show me the diff for CheckpointManager.java
You: Create a commit with message "Add checkpoint naming feature"
```

### 2. Multiple Tasks

```
You: Do three things:
     1. Add logging to TransactionCapture
     2. Fix the SQL injection vulnerability in SQL generation
     3. Update the README with the new changes
```

### 3. Code Review

```
You: Review this code for potential issues:
     [paste code]

You: Suggest improvements for performance
You: Check for security vulnerabilities
```

### 4. Explain Like I'm 5

```
You: Explain how binlog works in simple terms
You: What is GTID? Explain like I'm a beginner
You: Why do we need ROW format with FULL image?
```

---

## Common Tasks

### Development

| Task | Command |
|------|---------|
| Build project | `You: Build the project` |
| Run tests | `You: Run all tests` |
| Clean build | `You: Clean and rebuild` |
| Run CLI | `You: Start the CLI demo` |

### Code Modification

| Task | Command |
|------|---------|
| Add method | `You: Add a method to [class] that [does something]` |
| Fix bug | `You: Fix the bug where [description]` |
| Refactor | `You: Refactor [class/method] to improve [aspect]` |
| Add feature | `You: Implement [feature description]` |

### Documentation

| Task | Command |
|------|---------|
| Generate JavaDoc | `You: Add JavaDoc to all public methods in [class]` |
| Update README | `You: Update README with [new information]` |
| Create guide | `You: Create a guide for [topic]` |
| Document API | `You: Document the public API of this library` |

### Database

| Task | Command |
|------|---------|
| Create schema | `You: Generate SQL to create [table/schema]` |
| Add migration | `You: Create a migration script for [change]` |
| Fix query | `You: Optimize this SQL query: [paste query]` |
| Debug connection | `You: Help debug MySQL connection issue` |

---

## Advanced Usage

### Custom Configurations

```
You: Create a configuration file for different environments (dev, test, prod)
You: Add support for external configuration via application.properties
You: Implement configuration validation on startup
```

### Performance Optimization

```
You: Profile the checkpoint creation performance
You: Identify bottlenecks in the undo operation
You: Optimize the SQL generation for bulk operations
You: Add caching for column name lookups
```

### Monitoring and Logging

```
You: Add detailed logging to track checkpoint operations
You: Implement metrics collection for monitoring
You: Create a health check endpoint
You: Add alerts for binlog size growth
```

### Integration

```
You: Create a Spring Boot starter for this library
You: Add support for Spring Data JPA integration
You: Implement a Hibernate interceptor for automatic checkpoints
You: Create a REST controller for checkpoint operations
```

---

## Keyboard Shortcuts

### In Claude Code Terminal

| Shortcut | Action |
|----------|--------|
| `Ctrl+C` | Cancel current operation |
| `Ctrl+D` | Exit Claude Code |
| `↑` / `↓` | Navigate command history |
| `Tab` | Auto-complete (if supported) |

### File Navigation

```
You: Open CheckpointManager.java
You: Show me the schema.sql file
You: List all Java files in the project
```

---

## Troubleshooting Claude Code

### Issue: Claude doesn't understand context

**Solution:** Provide more details
```
You: I'm working on the MySQL checkpoint system.
     The project uses binlog to capture transactions.
     I need help with [specific issue]
```

### Issue: Response is too generic

**Solution:** Ask for specific implementation
```
You: Don't just explain, show me the actual code changes needed
You: Provide a complete working implementation, not pseudocode
```

### Issue: Suggested code doesn't work

**Solution:** Provide error details
```
You: That didn't work. Here's the error: [paste error]
     Here's my current code: [paste code]
     What's wrong?
```

---

## Resources

### Project Documentation

- **SETUP_GUIDE.md** - Complete setup instructions
- **README.md** - Quick start and API reference
- **ARCHITECTURE.md** - System architecture
- **TRANSACTION_BASED_CHECKPOINTS.md** - Design rationale

### Getting Help

```
You: Show me the project documentation structure
You: Explain the setup guide
You: What's the difference between snapshot and transaction-based approach?
```

### Learn More

```
You: Explain MySQL binlog in depth
You: How does GTID-based replication work?
You: What are the best practices for binlog configuration?
You: Show me resources for learning about CDC (Change Data Capture)
```

---

## Example Session

Here's a complete example session:

```bash
$ cd /home/user/low-code-mysql-checkpoint
$ claude

Claude Code v1.0

You: I'm new to this project. What does it do?

Claude: This is a MySQL Transaction-Based Checkpoint System that provides
        undo/redo functionality for database operations. It works by...
        [detailed explanation]

You: Show me the main classes

Claude: The main classes are:
        1. CheckpointManager - Creates and manages checkpoints
        2. TransactionCapture - Captures binlog events
        3. UndoRedoManager - Handles undo/redo operations
        [details for each]

You: I want to add a method to list all checkpoints for a user.
     Where should I add it?

Claude: You should add it to CheckpointManager class. Here's the implementation:
        [provides code]

You: Implement that for me

Claude: [Implements the method]
        ✅ Added getCheckpointsForUser() method to CheckpointManager

You: Now add tests for this method

Claude: [Creates test file with test cases]
        ✅ Created CheckpointManagerTest.java with 3 test cases

You: Build and run the tests

Claude: [Runs maven build]
        ✅ Build successful
        ✅ All tests passed (15 tests, 0 failures)

You: Great! Commit these changes

Claude: [Creates git commit]
        ✅ Committed: "Add getCheckpointsForUser method with tests"

You: Thanks!

Claude: You're welcome! Is there anything else you'd like to work on?

You: No, that's all for now

Claude: Happy coding! Type 'claude' to resume later.

$
```

---

## Best Commands to Try First

Start with these to get familiar:

1. **Understand the project:**
   ```
   You: Explain this project in simple terms
   You: What problem does this solve?
   ```

2. **Explore the code:**
   ```
   You: Show me the project structure
   You: What are the main components?
   ```

3. **Learn usage:**
   ```
   You: How do I use this in my application?
   You: Give me a complete working example
   ```

4. **Modify something small:**
   ```
   You: Add a toString() method to Checkpoint class
   You: Add more logging to TransactionCapture
   ```

5. **Run the demo:**
   ```
   You: Build and run the CLI demo
   You: Show me how to test undo/redo
   ```

---

**Ready to code with Claude!** 🚀

Start by asking Claude to explain the project or help with any specific task. Claude Code is here to make your development experience smoother and more productive.
