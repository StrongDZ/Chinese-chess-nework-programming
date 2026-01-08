#include "../../include/ai/ai_service.h"
#include <array>
#include <cstdlib>
#include <fstream>
#include <iostream>
#include <memory>
#include <sstream>

using namespace std;

AIService::AIService()
    : pythonPath("python3"), aiScriptPath(""), initialized(false) {}

AIService::~AIService() {}

bool AIService::initialize(const string &python, const string &aiScriptDir) {
  pythonPath = python.empty() ? "python3" : python;

  // Find AI script path
  if (!aiScriptDir.empty()) {
    aiScriptPath = aiScriptDir + "/ai.py";
  } else {
    // Try common locations
    vector<string> searchPaths = {"/opt/app/AI/ai.py", // Docker container path
                                  "../AI/ai.py", "../../AI/ai.py",
                                  "../../../AI/ai.py", "./AI/ai.py"};

    for (const auto &path : searchPaths) {
      ifstream f(path);
      if (f.good()) {
        aiScriptPath = path;
        break;
      }
    }
  }

  // Verify AI script exists
  if (aiScriptPath.empty()) {
    cerr << "[AIService] Could not find ai.py" << endl;
    return false;
  }

  ifstream f(aiScriptPath);
  if (!f.good()) {
    cerr << "[AIService] AI script not found at: " << aiScriptPath << endl;
    return false;
  }

  // Test Python availability
  string testCmd = pythonPath + " --version 2>&1";
  string result = "";

  array<char, 128> buffer;
  unique_ptr<FILE, decltype(&pclose)> pipe(popen(testCmd.c_str(), "r"), pclose);
  if (pipe) {
    while (fgets(buffer.data(), buffer.size(), pipe.get()) != nullptr) {
      result += buffer.data();
    }
  }

  if (result.find("Python") == string::npos) {
    cerr << "[AIService] Python not available at: " << pythonPath << endl;
    return false;
  }

  // Test Pikafish availability by checking if it exists
  string pikafishTestCmd =
      "which pikafish 2>/dev/null || command -v pikafish 2>/dev/null";
  string pikafishResult = "";

  array<char, 128> pikaBuf;
  unique_ptr<FILE, decltype(&pclose)> pikaPipe(
      popen(pikafishTestCmd.c_str(), "r"), pclose);
  if (pikaPipe) {
    while (fgets(pikaBuf.data(), pikaBuf.size(), pikaPipe.get()) != nullptr) {
      pikafishResult += pikaBuf.data();
    }
  }

  // Also check common locations
  vector<string> pikafishPaths = {
      "/opt/app/AI/pikafish", // Docker container path
      "/usr/local/bin/pikafish",
      "/usr/bin/pikafish",
      "./pikafish",
      "../pikafish",
      "./AI/pikafish",
      "../AI/pikafish"};

  bool pikafishFound = !pikafishResult.empty();
  if (!pikafishFound) {
    for (const auto &path : pikafishPaths) {
      ifstream pf(path);
      if (pf.good()) {
        pikafishFound = true;
        break;
      }
    }
  }

  if (!pikafishFound) {
    cerr << "[AIService] Pikafish engine not found. AI features will be "
            "unavailable."
         << endl;
    cerr << "[AIService] Install Pikafish from: "
            "https://github.com/official-pikafish/Pikafish"
         << endl;
    initialized = false;
    return false;
  }

  initialized = true;
  cout << "[AIService] Initialized with AI script: " << aiScriptPath << endl;
  return true;
}

bool AIService::isReady() const { return initialized; }

string AIService::difficultyToString(AIDifficulty difficulty) {
  switch (difficulty) {
  case AIDifficulty::EASY:
    return "easy";
  case AIDifficulty::MEDIUM:
    return "medium";
  case AIDifficulty::HARD:
    return "hard";
  default:
    return "medium";
  }
}

string AIService::executePythonAI(const string &xfen,
                                  const string &difficulty) {
  if (!initialized) {
    cerr << "[AIService::executePythonAI] Not initialized" << endl;
    return "";
  }

  // Use ai_simple.py for direct subprocess call to Pikafish
  // This avoids the blocking readline issue in the original ai.py
  string aiSimplePath =
      aiScriptPath.substr(0, aiScriptPath.rfind('/')) + "/ai_simple.py";

  stringstream cmd;
  cmd << pythonPath << " \"" << aiSimplePath << "\" ";
  cmd << "\"" << xfen << "\" ";
  cmd << "\"" << difficulty << "\" 2>&1";

  cout << "[AIService::executePythonAI] Executing: " << cmd.str() << endl;

  string result = "";
  string errorOutput = "";
  array<char, 256> buffer;

  // Open pipe and capture both stdout and stderr
  string fullCmd = cmd.str() + " 2>&1";
  unique_ptr<FILE, decltype(&pclose)> pipe(popen(fullCmd.c_str(), "r"), pclose);

  if (!pipe) {
    cerr << "[AIService::executePythonAI] Failed to open pipe" << endl;
    return "error";
  }

  while (fgets(buffer.data(), buffer.size(), pipe.get()) != nullptr) {
    string line = buffer.data();
    result += line;
    // Check if line contains error info
    if (line.find("error:") != string::npos ||
        line.find("stderr:") != string::npos) {
      errorOutput += line;
    }
  }

  // Log error output if any
  if (!errorOutput.empty()) {
    cerr << "[AIService::executePythonAI] Python script errors: " << errorOutput
         << endl;
  }

  // Trim whitespace
  while (!result.empty() && (result.back() == '\n' || result.back() == '\r' ||
                             result.back() == ' ')) {
    result.pop_back();
  }

  cout << "[AIService::executePythonAI] Result: " << result << endl;

  return result;
}

optional<AIMove> AIService::parseUCIMove(const string &uci) {
  if (uci.length() < 4 || uci == "error") {
    return nullopt;
  }

  // UCI format: "a0a1" where a=col(0-8), 0=row(0-9)
  // IMPORTANT: Pikafish UCI uses FEN coordinate system:
  // - Row 0 in UCI = top/black side (row 9 in board)
  // - Row 9 in UCI = bottom/red side (row 0 in board)
  // We need to flip: boardRow = 9 - uciRow
  char fromColChar = uci[0];
  char fromRowChar = uci[1];
  char toColChar = uci[2];
  char toRowChar = uci[3];

  if (!isalpha(fromColChar) || !isalpha(toColChar) || !isdigit(fromRowChar) ||
      !isdigit(toRowChar)) {
    return nullopt;
  }

  int uciFromRow = fromRowChar - '0';
  int uciToRow = toRowChar - '0';

  AIMove move;
  move.fromY = fromColChar - 'a'; // col: a=0, b=1, ..., i=8
  move.fromX = 9 - uciFromRow; // Flip row: UCI row 0 (top) = board row 9, UCI
                               // row 9 (bottom) = board row 0
  move.toY = toColChar - 'a';
  move.toX = 9 - uciToRow; // Flip row
  move.uci = uci;

  // Validate ranges
  if (move.fromX < 0 || move.fromX > 9 || move.fromY < 0 || move.fromY > 8 ||
      move.toX < 0 || move.toX > 9 || move.toY < 0 || move.toY > 8) {
    return nullopt;
  }

  return move;
}

AIResult AIService::predictMove(const string &xfen, AIDifficulty difficulty) {
  AIResult result;

  if (!initialized) {
    result.success = false;
    result.message = "AI service not initialized";
    return result;
  }

  string diffStr = difficultyToString(difficulty);
  string uciResult = executePythonAI(xfen, diffStr);

  if (uciResult.empty() || uciResult == "error") {
    result.success = false;
    result.message = "AI engine failed to find a move";
    return result;
  }

  auto move = parseUCIMove(uciResult);
  if (!move.has_value()) {
    result.success = false;
    result.message = "Failed to parse AI move: " + uciResult;
    return result;
  }

  result.success = true;
  result.message = "Move predicted successfully";
  result.move = move;
  return result;
}

AIResult AIService::predictMoveWithHistory(const string &initialXfen,
                                           const vector<string> &moves,
                                           AIDifficulty difficulty) {
  AIResult result;

  if (!initialized) {
    result.success = false;
    result.message = "AI service not initialized";
    return result;
  }

  // Build position string with move history
  stringstream positionStr;
  positionStr << "position fen " << initialXfen;

  if (!moves.empty()) {
    positionStr << " moves";
    for (const auto &m : moves) {
      positionStr << " " << m;
    }
  }

  // For position with history, we need different Python command
  string diffStr = difficultyToString(difficulty);
  stringstream cmd;
  cmd << pythonPath << " -c \"";
  cmd << "import sys; sys.path.insert(0, '"
      << aiScriptPath.substr(0, aiScriptPath.rfind('/')) << "'); ";
  cmd << "from ai import AI, AIDifficulty; ";
  cmd << "ai = AI(); ";
  cmd << "if ai.initialize(): ";
  cmd << "  move = ai.predict_move('" << positionStr.str() << "', AIDifficulty."
      << (difficulty == AIDifficulty::EASY     ? "EASY"
          : difficulty == AIDifficulty::MEDIUM ? "MEDIUM"
                                               : "HARD")
      << "); ";
  cmd << "  if move: print(AI.move_to_uci(move)); ";
  cmd << "  else: print('error'); ";
  cmd << "  ai.shutdown(); ";
  cmd << "else: print('error')";
  cmd << "\" 2>&1";

  string uciResult = "";
  array<char, 256> buffer;
  unique_ptr<FILE, decltype(&pclose)> pipe(popen(cmd.str().c_str(), "r"),
                                           pclose);

  if (pipe) {
    while (fgets(buffer.data(), buffer.size(), pipe.get()) != nullptr) {
      uciResult += buffer.data();
    }
  }

  // Trim whitespace
  while (!uciResult.empty() &&
         (uciResult.back() == '\n' || uciResult.back() == '\r' ||
          uciResult.back() == ' ')) {
    uciResult.pop_back();
  }

  if (uciResult.empty() || uciResult == "error") {
    result.success = false;
    result.message = "AI engine failed to find a move";
    return result;
  }

  auto move = parseUCIMove(uciResult);
  if (!move.has_value()) {
    result.success = false;
    result.message = "Failed to parse AI move: " + uciResult;
    return result;
  }

  result.success = true;
  result.message = "Move predicted successfully";
  result.move = move;
  return result;
}

AIResult AIService::suggestMove(const string &xfen) {
  return predictMove(xfen, AIDifficulty::HARD);
}

string AIService::toUCI(int fromX, int fromY, int toX, int toY) {
  char fromCol = 'a' + fromY;
  char fromRow = '0' + fromX;
  char toCol = 'a' + toY;
  char toRow = '0' + toX;

  return string(1, fromCol) + string(1, fromRow) + string(1, toCol) +
         string(1, toRow);
}

optional<AIMove> AIService::fromUCI(const string &uci) {
  if (uci.length() < 4) {
    return nullopt;
  }

  // UCI format uses FEN coordinate system (row 0 = top/black, row 9 =
  // bottom/red) Need to flip to board coordinates (row 0 = bottom/red, row 9 =
  // top/black)
  int uciFromRow = uci[1] - '0';
  int uciToRow = uci[3] - '0';

  AIMove move;
  move.fromY = uci[0] - 'a';
  move.fromX = 9 - uciFromRow; // Flip row
  move.toY = uci[2] - 'a';
  move.toX = 9 - uciToRow; // Flip row
  move.uci = uci;

  if (move.fromX < 0 || move.fromX > 9 || move.fromY < 0 || move.fromY > 8 ||
      move.toX < 0 || move.toX > 9 || move.toY < 0 || move.toY > 8) {
    return nullopt;
  }

  return move;
}

string AIService::boardToXfen(const string board[10][9], const string &turn) {
  stringstream fen;

  // Convert from row 9 to row 0 (FEN order)
  for (int row = 9; row >= 0; row--) {
    int emptyCount = 0;
    for (int col = 0; col < 9; col++) {
      string piece = board[row][col];
      if (piece.empty() || piece == " ") {
        emptyCount++;
      } else {
        if (emptyCount > 0) {
          fen << emptyCount;
          emptyCount = 0;
        }
        fen << piece;
      }
    }
    if (emptyCount > 0) {
      fen << emptyCount;
    }
    if (row > 0) {
      fen << "/";
    }
  }

  fen << " " << turn << " - - 0 1";
  return fen.str();
}
