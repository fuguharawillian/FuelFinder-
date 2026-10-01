'use strict';
/* Paths */
var path = {
    dev: {
        html:       'dev/',
        js:         'dev/assets/js/',
        img:        'dev/assets/img/',
        fonts:      'dev/assets/fonts/',
        bundle:     'dev/assets/js/bundle/',
        css:        'dev/assets/css/',
        style:      'dev/assets/css/',
        bundles:    'dev/assets/cssbundle/',
        vendor:     'dev/assets/vendor/',
    },
    dist: {
        html:       'dist/',
        js:         'dist/assets/js/',
        img:        'dist/assets/img/',
        fonts:      'dist/assets/fonts/',
        bundle:     'dist/assets/js/bundle/',
        css:        'dist/assets/css/',
        style:      'dist/assets/css/',
        bundles:    'dist/assets/cssbundle/',
        vendor:    	'dist/assets/vendor/',
    },
    src: {
        html:       ['src/**/*.html', '!src/partials/**/*.html'],
        partials:   'src/partials/',
        js:         'src/assets/js/',
        img:        'src/assets/img/**/*.*',
        fonts:      'src/assets/fonts/**/*.*',
        themejs:    'src/assets/js/theme.js',
        vendorjs:   'src/assets/js/vendor/*.*',
        style:      'src/assets/scss/luno-style.scss',
        bundles:    'src/assets/cssbundle/**/*.*',
        vendor:     'src/assets/vendor/**/*.*',
    },
    watch: {
        html:       ['src/**/*.html'],
        partials:   'src/partials/**/*.*',
        themejs:    'src/assets/js/theme.js',
        img:        'src/assets/img/**/*.*',
        fonts:      'src/assets/fonts/**/*.*',
        css:        'src/assets/scss/**/*.scss*',
        bundles:    'src/assets/cssbundle/',
        bundle:     'dev/assets/js/bundle/',
        vendorjs:   'src/assets/js/vendor/*.*',
        vendor:		'src/assets/vendor/',
    },
    clean: {
        dev:        'dev/*',
        dist:       'dist/*',
    }
};

/* Include gulp and plugins */
var gulp = require('gulp'),
webserver = require('browser-sync'),
reload = webserver.reload,
plumber = require('gulp-plumber'),
sourcemaps = require('gulp-sourcemaps'),
sass = require('gulp-sass')(require('sass')),
sassUnicode = require('gulp-sass-unicode'),
autoprefixer = require('gulp-autoprefixer'),
cleanCSS = require('gulp-clean-css'),
uglify = require('gulp-uglify'),
cache = require('gulp-cache'),
imagemin = require('gulp-imagemin'),
jpegrecompress = require('imagemin-jpeg-recompress'),
pngquant = require('imagemin-pngquant'),
del = require('del'),
fileinclude = require('gulp-file-include'),
beautify = require('gulp-beautify'),
minify = require('gulp-minify'),
concat = require('gulp-concat'),
jsImport = require('gulp-js-import'),
newer = require('gulp-newer'),
replace = require('gulp-replace'),
touch = require('gulp-touch-cmd');
var rename = require('gulp-rename');

/* Server */
var config = {
    server: {
        baseDir: './dist'
    },
    ghostMode: false, // By setting true, clicks, scrolls and form inputs on any device will be mirrored to all others
    notify: false
};

// Start the server
gulp.task('webserver', function () { webserver(config); });

// Compile html
gulp.task('html:dev', function () {
    return gulp.src(path.src.html)
    .pipe(newer({ dest: path.dev.html, extra: path.watch.partials }))
    .pipe(plumber())
    .pipe(fileinclude({ prefix: '@@', basepath: path.src.partials }))
    .pipe(beautify.html({ indent_size: 2, preserve_newlines: false }))
    .pipe(gulp.dest(path.dev.html))
    .pipe(touch())
});
gulp.task('html:dist', function () {
    return gulp.src(path.src.html)
    .pipe(newer({ dest: path.dist.html, extra: path.watch.partials }))
    .pipe(plumber())
    .pipe(fileinclude({ prefix: '@@', basepath: path.src.partials }))
    .pipe(beautify.html({ indent_size: 2, preserve_newlines: false }))
    .pipe(gulp.dest(path.dist.html))
    .pipe(touch())
    .on('end', () => { reload(); });
});

// Compile theme styles
gulp.task('css:dev', function () {
    return gulp.src(path.src.style)
    .pipe(newer(path.dev.style))
    .pipe(plumber())
    .pipe(sass()
      .on('error', function (err) {
        sass.logError(err);
        this.emit('end');
      })
    )
    .pipe(sassUnicode())
    .pipe(autoprefixer())
    .pipe(beautify.css({ indent_size: 2, preserve_newlines: false, newline_between_rules: false }))
    .pipe(gulp.dest(path.dev.style))
    .pipe(touch())
});
gulp.task('css:dist', function () {
    return gulp.src(path.src.style)
    .pipe(newer(path.dist.style))
    .pipe(plumber())
    .pipe(sourcemaps.init())
    .pipe(sass()
      .on('error', function (err) {
        sass.logError(err);
        this.emit('end');
      })
    )
    .pipe(sassUnicode())
    .pipe(autoprefixer())
    .pipe(cleanCSS())
    .pipe(sourcemaps.write('.'))
    .pipe(gulp.dest(path.dist.style))
    .pipe(touch())
    .on('end', () => { reload(); });
});

// Move fonts
gulp.task('fonts:dev', function () {
    return gulp.src(path.src.fonts)
    .pipe(newer(path.dev.fonts))
    .pipe(gulp.dest(path.dev.fonts));
});
gulp.task('fonts:dist', function () {
    return gulp.src(path.src.fonts)
    .pipe(newer(path.dist.fonts))
    .pipe(gulp.dest(path.dist.fonts));
});

// Move vendor css 
gulp.task('bundles:dev', function () {
    return gulp.src(path.src.bundles)
    .pipe(newer(path.dev.bundles))
    .pipe(gulp.dest(path.dev.bundles));
});
gulp.task('bundles:dist', function () {
    return gulp.src(path.src.bundles)
    .pipe(newer(path.dist.bundles))
    .pipe(gulp.dest(path.dist.bundles));
});

// Move vendor js and css file 
gulp.task('vendor:dev', function () {
    return gulp.src(path.src.vendor)
    .pipe(newer(path.dev.vendor))
    .pipe(gulp.dest(path.dev.vendor));
});
gulp.task('vendor:dist', function () {
    return gulp.src(path.src.vendor)
    .pipe(newer(path.dist.vendor))
    .pipe(gulp.dest(path.dist.vendor));
});

// Compile vendor plugins js
gulp.task('pluginsjs:dev', function() {
    return gulp.src([
        'node_modules/jquery/dist/jquery.js',
        'node_modules/bootstrap/dist/js/bootstrap.bundle.js',
        'src/assets/vendor/colorpicker/colorpicker.js',
        path.src.vendorjs
    ])
    .pipe(jsImport({hideConsole: true}))
    .pipe(concat('plugins.js'))
    .pipe(gulp.dest(path.dev.js))
    .pipe(touch())
});
gulp.task('pluginsjs:dist', function() {
    console.log(path.src.vendorjs);
    return gulp.src([
        'node_modules/jquery/dist/jquery.js',
        'node_modules/bootstrap/dist/js/bootstrap.bundle.js',
        'src/assets/vendor/colorpicker/colorpicker.js',
        path.src.vendorjs
    ])
    .pipe(jsImport({hideConsole: true}))
    .pipe(concat('plugins.js'))
    .pipe(uglify())
    .pipe(gulp.dest(path.dist.js))
    .pipe(touch())
    .on('end', () => { reload(); });
});

// Compile theme js
gulp.task('themejs:dev', function () {
    return gulp.src(path.src.themejs)
    .pipe(gulp.dest(path.dev.js))
    .pipe(plumber())
    .pipe(gulp.dest(path.dev.js))
});
gulp.task('themejs:dist', function () {
    return gulp.src(path.src.themejs)
    .pipe(gulp.dest(path.dist.js))
    .pipe(plumber())
    .pipe(uglify())
    .pipe(gulp.dest(path.dist.js))
    .on('end', () => { reload(); });
});

// Image processing
gulp.task('image:dev', function () {
    return gulp.src(path.src.img)
    .pipe(newer(path.dev.img))
    .pipe(cache(imagemin([
        imagemin.gifsicle({ interlaced: true }),
        jpegrecompress({
            progressive: true,
            max: 90,
            min: 80
        }),
        pngquant(),
        imagemin.svgo({ plugins: [{ removeViewBox: false }] })])))
    .pipe(gulp.dest(path.dev.img));
});
gulp.task('image:dist', function () {
    return gulp.src(path.src.img)
    .pipe(newer(path.dist.img))
    .pipe(cache(imagemin([
        imagemin.gifsicle({ interlaced: true }),
        jpegrecompress({
            progressive: true,
            max: 90,
            min: 80
        }),
        pngquant(),
        imagemin.svgo({ plugins: [{ removeViewBox: false }] })
    ])))
    .pipe(gulp.dest(path.dist.img))
    .on('end', () => { reload(); });
});

gulp.task('npm:dev', function (done) {
    // array of all the js paths you want to bundle.
    var scriptSourcesArr = [		
		{
			fileName: "libscripts.bundle.js",
			source: [
				"node_modules/jquery/dist/jquery.js", 					    // jQuery plugin file
				"node_modules/bootstrap/dist/js/bootstrap.bundle.js", 	    // Bootstrap js file
				"src/assets/vendor/colorpicker/colorpicker.js",		        // Colorpicker js file
			]
		},{
			fileName: "apexcharts.bundle.js",
			source: ["node_modules/apexcharts/dist/apexcharts.min.js"]
		},{
			fileName: "chartist.bundle.js",
			source: ["node_modules/chartist/dist/chartist.js"]
		},{
			fileName: "jqueryknob.bundle.js",
			source: ["node_modules/jquery-knob/dist/jquery.knob.min.js"]
		},{
			fileName: "sparkline.bundle.js",
			source: ["node_modules/jquery-sparkline/jquery.sparkline.js"]
		},{
			fileName: "summernote.bundle.js",
			source: ["node_modules/summernote/dist/summernote.min.js"]
		},{
			fileName: "dropify.bundle.js",
			source: ["node_modules/dropify/dist/js/dropify.min.js"]
		},{
			fileName: "select2.bundle.js",
			source: ["node_modules/select2/dist/js/select2.js"]
		},{
			fileName: "sweetalert2.bundle.js",
			source: ["node_modules/sweetalert2/dist/sweetalert2.all.min.js"]
		},{
			fileName: "daterangepicker.bundle.js",
			source: ["node_modules/daterangepicker/moment.min.js", "node_modules/daterangepicker/daterangepicker.js"]
		},{
			fileName: "bootstrapdatepicker.bundle.js",
			source: ["node_modules/bootstrap-datepicker/dist/js/bootstrap-datepicker.min.js"]
		},{
			fileName: "rangeslider.bundle.js",
			source: ["node_modules/ion-rangeslider/js/ion.rangeSlider.js"]
		},{
			fileName: "toastr.bundle.js",
			source: ["node_modules/toastr/build/toastr.min.js"]
		},{
			fileName: "fullcalendar.bundle.js",
			source: ["node_modules/fullcalendar/main.min.js"]
		},{
			fileName: "dataTables.bundle.js",
			source: ["node_modules/datatables.net/js/jquery.dataTables.js","node_modules/datatables.net-bs5/js/dataTables.bootstrap5.js","node_modules/datatables.net-responsive/js/dataTables.responsive.js"]
		},{
			fileName: "jsgrid.bundle.js",
			source: ["node_modules/jsgrid/dist/jsgrid.js"]
		},{
			fileName: "nestable.bundle.js",
			source: ["node_modules/jquery-nestable/jquery.nestable.js"]
		},{
			fileName: "owlcarousel.bundle.js",
			source: ["node_modules/owl.carousel/dist/owl.carousel.min.js"]
		},{
			fileName: "masonry.bundle.js",
			source: ["node_modules/masonry-layout/dist/masonry.pkgd.js"]
		},{
			fileName: "nouislider.bundle.js",
			source: ["node_modules/nouislider/dist/nouislider.min.js"]
		},{
			fileName: "fancybox.bundle.js",
			source: ["node_modules/jquery.fancybox/source/jquery.fancybox.js"]
		},{
			fileName: "skedtape.bundle.js",
			source: ["node_modules/jquery-sked-tape/dist/jquery.skedTape.js"]
		},{
			fileName: "typedjs.bundle.js",
			source: ["node_modules/typed.js/lib/typed.js","node_modules/typed.js/assets/demos.js"]
		},{
			fileName: "jquerycounterup.bundle.js",
			source: ["node_modules/waypoints/lib/jquery.waypoints.js","node_modules/jquery.counterup/jquery.counterup.js"]
		},{
			fileName: "cropper.bundle.js",
			source: ["node_modules/cropper/dist/cropper.min.js"]
		},{
			fileName: "clipboard.bundle.js",
			source: ["node_modules/clipboard/dist/clipboard.min.js"]
		},{
			fileName: "flatpickr.bundle.js",
			source: ["node_modules/flatpickr/dist/flatpickr.js"]
		},{
			fileName: "inputmask.bundle.js",
			source: ["node_modules/inputmask/dist/inputmask.js"]
		},{
			fileName: "bs-maxlength.bundle.js",
			source: ["node_modules/bootstrap-maxlength/dist/bootstrap-maxlength.js"]
		},{
			fileName: "bootstraptagsinput.bundle.js",
			source: ["node_modules/bootstrap-tagsinput/dist/bootstrap-tagsinput.js"]
		},{
			fileName: "multiselectsplitter.bundle.js",
			source: ["node_modules/bootstrap-multiselectsplitter/bootstrap-multiselectsplitter.js"]
		},{
			fileName: "jkanban.bundle.js",
			source: ["node_modules/jkanban/dist/jkanban.js"]
		},{
			fileName: "jquerysteps.bundle.js",
			source: ["node_modules/jquery.steps/dist/jquery-steps.js"]
		},{
			fileName: "jquerysteps.bundle.js",
			source: ["node_modules/jquery.steps/dist/jquery-steps.js"]
		},{
			fileName: "xEditable.bundle.js",
			source: ["src/assets/vendor/x-editable/moment.min.js","node_modules/x-editable-4-bs4/dist/bootstrap4-editable/js/bootstrap-editable.min.js"]
		},{
			fileName: "tabledragger.bundle.js",
			source: ["src/assets/vendor/table-dragger/table-dragger.min.js"]
		},{
			fileName: "invoice.bundle.js",
			source: ["src/assets/vendor/invoice/beacon.min.js","src/assets/vendor/invoice/example.js"]
		},{
			fileName: "tui-calendar.bundle.js",
			source: ["src/assets/vendor/tui-calendar/tui-code-snippet.min.js","src/assets/vendor/tui-calendar/tui-time-picker.min.js","src/assets/vendor/tui-calendar/tui-date-picker.min.js","src/assets/vendor/tui-calendar/moment.min.js","src/assets/vendor/tui-calendar/chance.min.js","src/assets/vendor/tui-calendar/tui-calendar.js","src/assets/vendor/tui-calendar/calendars.js","src/assets/vendor/tui-calendar/schedules.js","src/assets/vendor/tui-calendar/app.js"]
		},{
			fileName: "swiper.bundle.js",
			source: ["node_modules/swiper/swiper-bundle.min.js"]
		},{
			fileName: "purecounter.bundle.js",
			source: ["node_modules/@srexi/purecounterjs/js/purecounter_vanilla.js"]
		},{
			fileName: "jspreadsheet.bundle.js",
			source: ["node_modules/jspreadsheet-ce/dist/index.js","node_modules/jsuites/dist/jsuites.js"]
		},{
			fileName: "rating.bundle.js",
			source: ["node_modules/jquery-bar-rating/dist/jquery.barrating.min.js"]
		},{
			fileName: "tagify.bundle.js",
			source: ["src/assets/vendor/tagify/tagify.min.js","src/assets/vendor/tagify/tagify.polyfills.min.js"]
		}
	];
	
	scriptSourcesArr.forEach(function(gulpObject) {
		gulp.src(gulpObject.source) 				// name of the new file all your js files are to be bundled to.
        .pipe(concat(gulpObject.fileName)) 			// the destination where the new bundled file is going to be saved to.
        .pipe(gulp.dest(path.dev.bundle));
    });
    done();
});

gulp.task('npm:dist', function (done) {
    // array of all the js paths you want to bundle.
    var scriptSourcesArr = [
		{
			fileName: 	"bootstrapdatepicker.min.css",
			source: 	["node_modules/bootstrap-datepicker/dist/css/bootstrap-datepicker.min.css"]
		},
		{
			fileName: "libscripts.bundle.js",
			source: [
				"node_modules/jquery/dist/jquery.js", 					    // jQuery plugin file
				"node_modules/bootstrap/dist/js/bootstrap.bundle.js", 	    // Bootstrap js file
				"src/assets/vendor/colorpicker/colorpicker.js",		        // Colorpicker js file
			]
		},{
			fileName: "apexcharts.bundle.js",
			source: ["node_modules/apexcharts/dist/apexcharts.min.js"]
		},{
			fileName: "chartist.bundle.js",
			source: ["node_modules/chartist/dist/chartist.js"]
		},{
			fileName: "jqueryknob.bundle.js",
			source: ["node_modules/jquery-knob/dist/jquery.knob.min.js"]
		},{
			fileName: "sparkline.bundle.js",
			source: ["node_modules/jquery-sparkline/jquery.sparkline.js"]
		},{
			fileName: "summernote.bundle.js",
			source: ["node_modules/summernote/dist/summernote.min.js"]
		},{
			fileName: "dropify.bundle.js",
			source: ["node_modules/dropify/dist/js/dropify.min.js"]
		},{
			fileName: "select2.bundle.js",
			source: ["node_modules/select2/dist/js/select2.js"]
		},{
			fileName: "sweetalert2.bundle.js",
			source: ["node_modules/sweetalert2/dist/sweetalert2.all.min.js"]
		},{
			fileName: "daterangepicker.bundle.js",
			source: ["node_modules/daterangepicker/moment.min.js", "node_modules/daterangepicker/daterangepicker.js"]
		},{
			fileName: "bootstrapdatepicker.bundle.js",
			source: ["node_modules/bootstrap-datepicker/dist/js/bootstrap-datepicker.min.js"]
		},{
			fileName: "rangeslider.bundle.js",
			source: ["node_modules/ion-rangeslider/js/ion.rangeSlider.js"]
		},{
			fileName: "toastr.bundle.js",
			source: ["node_modules/toastr/build/toastr.min.js"]
		},{
			fileName: "fullcalendar.bundle.js",
			source: ["node_modules/fullcalendar/main.min.js"]
		},{
			fileName: "dataTables.bundle.js",
			source: ["node_modules/datatables.net/js/jquery.dataTables.js","node_modules/datatables.net-bs5/js/dataTables.bootstrap5.js","node_modules/datatables.net-responsive/js/dataTables.responsive.js"]
		},{
			fileName: "jsgrid.bundle.js",
			source: ["node_modules/jsgrid/dist/jsgrid.js"]
		},{
			fileName: "nestable.bundle.js",
			source: ["node_modules/jquery-nestable/jquery.nestable.js"]
		},{
			fileName: "owlcarousel.bundle.js",
			source: ["node_modules/owl.carousel/dist/owl.carousel.min.js"]
		},{
			fileName: "masonry.bundle.js",
			source: ["node_modules/masonry-layout/dist/masonry.pkgd.js"]
		},{
			fileName: "nouislider.bundle.js",
			source: ["node_modules/nouislider/dist/nouislider.min.js"]
		},{
			fileName: "fancybox.bundle.js",
			source: ["node_modules/jquery.fancybox/source/jquery.fancybox.js"]
		},{
			fileName: "skedtape.bundle.js",
			source: ["node_modules/jquery-sked-tape/dist/jquery.skedTape.js"]
		},{
			fileName: "typedjs.bundle.js",
			source: ["node_modules/typed.js/lib/typed.js","node_modules/typed.js/assets/demos.js"]
		},{
			fileName: "jquerycounterup.bundle.js",
			source: ["node_modules/waypoints/lib/jquery.waypoints.js","node_modules/jquery.counterup/jquery.counterup.js"]
		},{
			fileName: "cropper.bundle.js",
			source: ["node_modules/cropper/dist/cropper.min.js"]
		},{
			fileName: "clipboard.bundle.js",
			source: ["node_modules/clipboard/dist/clipboard.min.js"]
		},{
			fileName: "flatpickr.bundle.js",
			source: ["node_modules/flatpickr/dist/flatpickr.js"]
		},{
			fileName: "inputmask.bundle.js",
			source: ["node_modules/inputmask/dist/inputmask.js"]
		},{
			fileName: "bs-maxlength.bundle.js",
			source: ["node_modules/bootstrap-maxlength/dist/bootstrap-maxlength.js"]
		},{
			fileName: "bootstraptagsinput.bundle.js",
			source: ["node_modules/bootstrap-tagsinput/dist/bootstrap-tagsinput.js"]
		},{
			fileName: "multiselectsplitter.bundle.js",
			source: ["node_modules/bootstrap-multiselectsplitter/bootstrap-multiselectsplitter.js"]
		},{
			fileName: "jkanban.bundle.js",
			source: ["node_modules/jkanban/dist/jkanban.js"]
		},{
			fileName: "jquerysteps.bundle.js",
			source: ["node_modules/jquery.steps/dist/jquery-steps.js"]
		},{
			fileName: "jquerysteps.bundle.js",
			source: ["node_modules/jquery.steps/dist/jquery-steps.js"]
		},{
			fileName: "xEditable.bundle.js",
			source: ["src/assets/vendor/x-editable/moment.min.js","node_modules/x-editable-4-bs4/dist/bootstrap4-editable/js/bootstrap-editable.min.js"]
		},{
			fileName: "tabledragger.bundle.js",
			source: ["src/assets/vendor/table-dragger/table-dragger.min.js"]
		},{
			fileName: "invoice.bundle.js",
			source: ["src/assets/vendor/invoice/beacon.min.js","src/assets/vendor/invoice/example.js"]
		},{
			fileName: "tui-calendar.bundle.js",
			source: ["src/assets/vendor/tui-calendar/tui-code-snippet.min.js","src/assets/vendor/tui-calendar/tui-time-picker.min.js","src/assets/vendor/tui-calendar/tui-date-picker.min.js","src/assets/vendor/tui-calendar/moment.min.js","src/assets/vendor/tui-calendar/chance.min.js","src/assets/vendor/tui-calendar/tui-calendar.js","src/assets/vendor/tui-calendar/calendars.js","src/assets/vendor/tui-calendar/schedules.js","src/assets/vendor/tui-calendar/app.js"]
		},{
			fileName: "swiper.bundle.js",
			source: ["node_modules/swiper/swiper-bundle.min.js"]
		},{
			fileName: "purecounter.bundle.js",
			source: ["node_modules/@srexi/purecounterjs/js/purecounter_vanilla.js"]
		},{
			fileName: "jspreadsheet.bundle.js",
			source: ["node_modules/jspreadsheet-ce/dist/index.js","node_modules/jsuites/dist/jsuites.js"]
		},{
			fileName: "rating.bundle.js",
			source: ["node_modules/jquery-bar-rating/dist/jquery.barrating.min.js"]
		},{
			fileName: "tagify.bundle.js",
			source: ["src/assets/vendor/tagify/tagify.min.js","src/assets/vendor/tagify/tagify.polyfills.min.js"]
		}
	];
	
	scriptSourcesArr.forEach(function(gulpObject) {
		gulp.src(gulpObject.source) 				// name of the new file all your js files are to be bundled to.
        .pipe(concat(gulpObject.fileName)) 			// the destination where the new bundled file is going to be saved to.
        .pipe(gulp.dest(path.dist.bundle));
    });
    done();
});

// Remove catalog dev
gulp.task('clean:dev', function () {
    return del(path.clean.dev);
});
gulp.task('clean:dist', function () {
    return del(path.clean.dist);
});

// Clear cache
gulp.task('cache:clear', function () {
    cache.clearAll();
});

// Assembly Dev
gulp.task('build:dev', gulp.series('clean:dev',
    gulp.parallel(
        'html:dev',
        'css:dev',
        'pluginsjs:dev',
        'npm:dev',
        'themejs:dev',
        'fonts:dev',
        'bundles:dev',
        'vendor:dev',
        'image:dev'
    ))
);

// Assembly Dist
gulp.task('build:dist', gulp.series('clean:dist',
    gulp.parallel(
        'html:dist',
        'css:dist',
        'pluginsjs:dist',
        'npm:dist',
        'themejs:dist',
        'fonts:dist',
        'bundles:dist',
        'vendor:dist',
        'image:dist'
    ))
);

// Launching tasks when files change
gulp.task('watch', function () {
    gulp.watch(path.watch.html, gulp.series('html:dist'));
    gulp.watch(path.watch.css, gulp.series('css:dist'));
    gulp.watch(path.watch.vendorjs, gulp.series('pluginsjs:dist'));
    gulp.watch(path.watch.bundle, gulp.series('npm:dist'));
    gulp.watch(path.watch.themejs, gulp.series('themejs:dist'));
    gulp.watch(path.watch.img, gulp.series('image:dist'));
    gulp.watch(path.watch.fonts, gulp.series('fonts:dist'));
    gulp.watch(path.watch.bundles, gulp.series('bundles:dist'));
    gulp.watch(path.watch.vendor, gulp.series('vendor:dist'));
});

// Serve
gulp.task('serve', gulp.series( gulp.parallel('webserver','watch') ));

// Dev
gulp.task('build:dev', gulp.series( 'build:dev' ));

// Dist
gulp.task('build:dist', gulp.series( 'build:dist' ));

// Default tasks
gulp.task('default', gulp.series( 'build:dist', gulp.parallel('webserver','watch') ));