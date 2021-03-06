'use strict';

var webpack = require('webpack');
var _       = require('lodash');

module.exports = _.merge(require('./webpack.config.js'), {
    plugins: [
        new webpack.optimize.CommonsChunkPlugin({
            name: "crud"
        }),
        new webpack.optimize.OccurenceOrderPlugin(),
        new webpack.optimize.DedupePlugin(),
        new webpack.optimize.UglifyJsPlugin({
            compress: {
                warnings: false,
                drop_console: true,
                hoist_vars: true,
                unsafe: true
            }
        })
    ]
});