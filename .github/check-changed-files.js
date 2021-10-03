module.exports = async ({github, context}) => {
  const result = await github.pulls.listFiles({
    owner: context.payload.repository.owner.login,
    repo: context.payload.repository.name,
    pull_number: context.payload.number,
    per_page: 100,
  });

  const files = result.data.filter((file) => {
    const filename = file.filename
    // Markdown files are not tested
    return !filename.endsWith("md") &&
      // Exclude YAML files as long as they are not the PR workflow itself
      !(filename.endsWith("yml") && !filename.endsWith("pull_request.yml")) && !filename.endsWith("yaml") &&
      // Fastlane metadata does not need tests
      !filename.startsWith("fastlane/");
  });
  console.log(`Remaining changed files: ${files.map(file => file.filename)}`)
  return files.length != 0;
}
