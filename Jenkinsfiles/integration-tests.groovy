def functional_test(browser, base_dir) {
  // Define a parallel build that runs stand-alone Selenium for the given browser
  return {
    node {
      // Setup the pytest command
      def cmd = "py.test tests/functional" +
                " --driver Remote" +
                " --host selenium" +
                " --capability browserName ${browser}" +
                " --base-url='${config.job.base_url}'" +
                " --junit-prefix=${browser}" +
                " --junit-xml=/app/test_results/functional-${browser}.xml" +
                " --reruns=2" +
                " -vv"
      if (config.job && config.job.tests) {
          cmd += " -m \"${config.job.tests}\""
      }
      if (config.job && config.job.maintenance_mode) {
          cmd += " --maintenance-mode"
      }
      // Timeout after 6 minutes, due to stalled nodes
      withEnv(["TIMEOUT=6m",
               "BROWSER=${browser}",
               "PYTEST_COMMAND=${cmd}",
               "SELENIUM_IMAGE_TAG=${config.job.selenium}",
               "COMPOSE_PROJECT_NAME=${browser}-${BUILD_TAG}",
               "COMPOSE_FILE=${base_dir}/docker-compose.selenium.yml"])
      {
          sh 'docker-compose pull selenium'
          sh 'docker-compose build tests'
          try {
              sh 'docker-compose run tests'
          } finally {
              sh 'docker-compose down --volumes --remove-orphans'
          }
      }
    }
  }
}

def headless_test(base_dir) {
  // Define a parallel build that runs the "headless" (requests, no Selenium) tests
  // return {
  //   node {
  //     def test_name = "kuma-test-headless-${BUILD_TAG}"
  //     dockerRun("kuma-integration-tests:${GIT_COMMIT_SHORT}",
  //                 ["docker_args": "--volume ${base_dir}/test_results:/test_results" +
  //                                 " --name ${test_name}" +
  //                                 " --user ${UID}",
  //                 "cmd": "py.test tests/headless" +
  //                         " --base-url='${config.job.base_url}'" +
  //                         " --junit-xml=/test_results/headless.xml" +
  //                         " --reruns=2"])
  //   }
  // }
  return {
    node {
      // Setup the pytest command
      def cmd = "py.test tests/headless" +
                " --base-url='${config.job.base_url}'" +
                " --junit-xml=/app/test_results/headless.xml" +
                " --reruns=2"
      // Timeout after 6 minutes, due to stalled nodes
      withEnv(["TIMEOUT=6m",
               "PYTEST_COMMAND=${cmd}",
               "COMPOSE_PROJECT_NAME=headless-${BUILD_TAG}",
               "COMPOSE_FILE=${base_dir}/docker-compose.selenium.yml"])
      {
          sh 'docker-compose build tests'
          try {
              sh "docker-compose run --no-deps tests"
          } finally {
              sh 'docker-compose down --volumes --remove-orphans'
          }
      }
    }
  }
}

stage('Test') {
    // Setup parallel tests
    def allTests = [:]
    def base_dir = pwd()
    allTests['chrome'] = functional_test('chrome', base_dir)
    allTests['firefox'] = functional_test('firefox', base_dir)
    allTests['headless'] = headless_test(base_dir)

    def nick =  "ci-bot"

    try {
        // Run the tests in parallel
        parallel allTests
        // Notify on success
        utils.notify_irc([irc_nick: nick, stage: 'Test', status: 'success'])
    } catch(err) {
        utils.notify_irc([irc_nick: nick, stage: 'Test', status: 'failure'])
        throw err
    }
}
