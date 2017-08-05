module.exports = function(config) {
  // same as :output-dir in cljsbuild/builds/test
  var output_dir = 'resources/public/test-web/out'
  // same as :output-to in cljsbuild/builds/test
  var output_to = 'resources/public/test-web/test.js'
  config.set({
    frameworks: ['cljs-test'],

    files: [
      output_dir + '/goog/base.js',
      output_dir + '/cljs_deps.js',
      output_to,
      {pattern: output_dir + '/*.js', included: false},
      {pattern: output_dir + '/**/*.js', included: false}
    ],

    client: {args: ['expound.test_runner.run_all'],
             captureConsole: true},

    // singleRun set to false does not work!
    singleRun: true,
    browsers: ['Chrome'],
    autoWatch: false,
    logLevel: config.LOG_INFO,
    concurrency: 1,
  })
};
